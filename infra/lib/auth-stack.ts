import * as cdk from 'aws-cdk-lib';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { Construct } from 'constructs';

// Firebase project is already embedded in the Android binary — not a secret.
const FIREBASE_PROJECT_ID = 'refitted-361ee';
const FIREBASE_OIDC_DOMAIN = `securetoken.google.com/${FIREBASE_PROJECT_ID}`;

interface AuthStackProps extends cdk.StackProps {
  table: dynamodb.Table;
}

export class AuthStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: AuthStackProps) {
    super(scope, id, props);

    // Group UUID is kept out of source via cdk.context.json (gitignored).
    // Set it locally or pass -c flags: cdk deploy -c paid1GroupId=xxx
    //
    // NOTE: the Paid1/Free/Anon DynamoDB policies' workout-program LeadingKeys are NOT
    // managed here. They're live admin-mutable data — the admin Lambdas
    // (RefittedUpdateIamGroup / RefittedUpdateDynamoGroup) rewrite those policy documents
    // (as new IAM policy versions) whenever a program is added/removed. If CDK owned that
    // content, every deploy would revert whatever the admin tools last wrote. So those three
    // policies are referenced by ARN only below, never created/updated by this stack.
    const paid1GroupId = this.node.tryGetContext('paid1GroupId') as string | undefined;

    const oidcProviderArn = `arn:aws:iam::${this.account}:oidc-provider/${FIREBASE_OIDC_DOMAIN}`;

    // ===========================================================================
    // Cognito Identity Pools
    // ===========================================================================

    const webPool = new cognito.CfnIdentityPool(this, 'WebPool', {
      identityPoolName: 'refitted',
      allowUnauthenticatedIdentities: false,
      openIdConnectProviderArns: [oidcProviderArn],
    });
    webPool.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    const androidPool = new cognito.CfnIdentityPool(this, 'AndroidPool', {
      identityPoolName: 'refitted_android',
      allowUnauthenticatedIdentities: false,
      openIdConnectProviderArns: [oidcProviderArn],
    });
    androidPool.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    // ===========================================================================
    // IAM Policies
    // ===========================================================================

    const tableArn = props.table.tableArn;
    const reverseIndexArn = `${tableArn}/index/Reverse-index`;

    // Paid1/Free/Anon policy content is owned by the admin Lambdas (see note above) —
    // reference by ARN only, never create/import as CDK-managed resources.
    const paid1PolicyArn = `arn:aws:iam::${this.account}:policy/DynamoDb-Refitted.Dev01-Paid1`;
    const freePolicyArn = `arn:aws:iam::${this.account}:policy/DynamoDb-Refitted.Dev01-Free`;
    const anonPolicyArn = `arn:aws:iam::${this.account}:policy/DynamoDb-Refitted.Dev01-Anon`;

    const paid1Policy = iam.ManagedPolicy.fromManagedPolicyArn(this, 'DynamoDbPaid1', paid1PolicyArn);
    const freePolicy = iam.ManagedPolicy.fromManagedPolicyArn(this, 'DynamoDbFree', freePolicyArn);
    const anonPolicy = iam.ManagedPolicy.fromManagedPolicyArn(this, 'DynamoDbAnon', anonPolicyArn);

    const readOnlyPolicy = new iam.ManagedPolicy(this, 'DynamoDbReadOnly', {
      managedPolicyName: 'DynamoDb-Refitted.Dev01-Read-Only',
      statements: [new iam.PolicyStatement({
        actions: ['dynamodb:BatchGetItem', 'dynamodb:GetItem', 'dynamodb:Query'],
        resources: [tableArn],
      })],
    });
    readOnlyPolicy.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    // Grants the admin Lambda permission to update the Paid1 policy versions.
    const iamEditGroupsPolicy = new iam.ManagedPolicy(this, 'IamEditGroups', {
      managedPolicyName: 'IAM-Refitted-EditGroups',
      statements: [new iam.PolicyStatement({
        sid: 'VisualEditor0',
        actions: [
          'iam:GetPolicyVersion', 'iam:ListPolicyVersions',
          'iam:CreatePolicyVersion', 'iam:DeletePolicyVersion',
        ],
        resources: [paid1Policy.managedPolicyArn],
      })],
    });
    iamEditGroupsPolicy.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    // Grants web admin users permission to invoke the Firebase user-listing Lambda.
    const invokeLambdaPolicy = new iam.ManagedPolicy(this, 'InvokeFirebaseLambda', {
      managedPolicyName: 'Refitted-InvokeFirebaseAdminLambda',
      statements: [new iam.PolicyStatement({
        sid: 'VisualEditor0',
        actions: ['lambda:InvokeFunction', 'lambda:InvokeAsync'],
        resources: [`arn:aws:lambda:${this.region}:${this.account}:function:RefittedListFirebaseUsers`],
      })],
    });
    invokeLambdaPolicy.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    // Per-user DynamoDB access scoped to the user's own sub (Firebase subject claim).
    const testingPolicy = new iam.ManagedPolicy(this, 'DynamoDbTesting', {
      managedPolicyName: 'DynamoDb-Role-Permissions-Testing',
      statements: [new iam.PolicyStatement({
        actions: ['dynamodb:GetItem', 'dynamodb:Query', 'dynamodb:UpdateItem'],
        resources: [tableArn, reverseIndexArn],
        conditions: {
          'ForAllValues:StringEquals': {
            'dynamodb:LeadingKeys': [
              'Plan',
              `\${${FIREBASE_OIDC_DOMAIN}:sub}`,
            ],
          },
        },
      })],
    });
    testingPolicy.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    // ===========================================================================
    // IAM Roles
    // ===========================================================================

    // Unauth roles are AWS auto-generated at Identity Pool creation time; import by name.
    const webUnauthRole = iam.Role.fromRoleName(this, 'WebUnauthRole', 'Cognito_refittedUnauth_Role');
    const androidUnauthRole = iam.Role.fromRoleName(this, 'AndroidUnauthRole', 'Cognito_refitted_androidUnauth_Role');

    const webAuthRole = new iam.Role(this, 'WebAuthRole', {
      roleName: 'Cognito_refittedAuth_Role',
      assumedBy: new iam.WebIdentityPrincipal('cognito-identity.amazonaws.com', {
        StringEquals: { 'cognito-identity.amazonaws.com:aud': webPool.ref },
        'ForAnyValue:StringLike': { 'cognito-identity.amazonaws.com:amr': FIREBASE_OIDC_DOMAIN },
      }),
    });
    webAuthRole.addManagedPolicy(readOnlyPolicy);
    webAuthRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonDynamoDBReadOnlyAccess'));
    webAuthRole.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    // Android auth role is assumed by any authenticated user (web or Android pool).
    const androidAuthRole = new iam.Role(this, 'AndroidAuthRole', {
      roleName: 'Cognito_refitted_androidAuth_Role',
      assumedBy: new iam.WebIdentityPrincipal('cognito-identity.amazonaws.com', {
        StringEquals: {
          'cognito-identity.amazonaws.com:aud': [androidPool.ref, webPool.ref],
        },
        'ForAnyValue:StringLike': { 'cognito-identity.amazonaws.com:amr': 'authenticated' },
      }),
    });
    androidAuthRole.addManagedPolicy(anonPolicy);
    androidAuthRole.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    const androidPaid1Role = new iam.Role(this, 'AndroidPaid1Role', {
      roleName: 'Cognito_refitted_androidPaid1_Role',
      assumedBy: new iam.WebIdentityPrincipal('cognito-identity.amazonaws.com', {
        StringEquals: { 'cognito-identity.amazonaws.com:aud': androidPool.ref },
        'ForAnyValue:StringLike': { 'cognito-identity.amazonaws.com:amr': FIREBASE_OIDC_DOMAIN },
      }),
    });
    androidPaid1Role.addManagedPolicy(paid1Policy);
    androidPaid1Role.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    const androidFreeRole = new iam.Role(this, 'AndroidFreeRole', {
      roleName: 'Cognito_refitted_androidFree_Role',
      assumedBy: new iam.WebIdentityPrincipal('cognito-identity.amazonaws.com', {
        StringEquals: { 'cognito-identity.amazonaws.com:aud': androidPool.ref },
        'ForAnyValue:StringLike': { 'cognito-identity.amazonaws.com:amr': FIREBASE_OIDC_DOMAIN },
      }),
    });
    androidFreeRole.addManagedPolicy(freePolicy);
    androidFreeRole.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    const firebaseAdminRole = new iam.Role(this, 'FirebaseAdminRole', {
      roleName: 'RefittedFirebaseAdmin',
      assumedBy: new iam.WebIdentityPrincipal('cognito-identity.amazonaws.com', {
        StringEquals: { 'cognito-identity.amazonaws.com:aud': webPool.ref },
        'ForAnyValue:StringLike': { 'cognito-identity.amazonaws.com:amr': FIREBASE_OIDC_DOMAIN },
      }),
    });
    firebaseAdminRole.addManagedPolicy(testingPolicy);
    firebaseAdminRole.addManagedPolicy(invokeLambdaPolicy);
    firebaseAdminRole.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    // ===========================================================================
    // Identity Pool Role Attachments
    // ===========================================================================

    const webPoolRoles = new cognito.CfnIdentityPoolRoleAttachment(this, 'WebPoolRoles', {
      identityPoolId: webPool.ref,
      roles: {
        authenticated: webAuthRole.roleArn,
        unauthenticated: webUnauthRole.roleArn,
      },
      roleMappings: {
        firebase: {
          type: 'Rules',
          ambiguousRoleResolution: 'AuthenticatedRole',
          identityProvider: oidcProviderArn,
          rulesConfiguration: {
            rules: [{
              claim: 'admin',
              matchType: 'Equals',
              roleArn: firebaseAdminRole.roleArn,
              value: 'true',
            }],
          },
        },
      },
    });
    webPoolRoles.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    const androidRules: cognito.CfnIdentityPoolRoleAttachment.MappingRuleProperty[] = [];
    if (paid1GroupId) {
      androidRules.push({
        claim: 'group',
        matchType: 'Equals',
        roleArn: androidPaid1Role.roleArn,
        value: paid1GroupId,
      });
    }
    androidRules.push({
      claim: 'email',
      matchType: 'Contains',
      roleArn: androidFreeRole.roleArn,
      value: '@',
    });

    const androidPoolRoles = new cognito.CfnIdentityPoolRoleAttachment(this, 'AndroidPoolRoles', {
      identityPoolId: androidPool.ref,
      roles: {
        authenticated: androidAuthRole.roleArn,
        unauthenticated: androidUnauthRole.roleArn,
      },
      roleMappings: {
        firebase: {
          type: 'Rules',
          ambiguousRoleResolution: 'AuthenticatedRole',
          identityProvider: oidcProviderArn,
          rulesConfiguration: { rules: androidRules },
        },
      },
    });
    androidPoolRoles.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);
  }
}
