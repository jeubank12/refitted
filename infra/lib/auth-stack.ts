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

    // NOTE: the Free/Anon DynamoDB policies' workout-program LeadingKeys, and every paid
    // group's role/policy/role-mapping-rule, are NOT managed here. They're live admin-mutable
    // data — the web admin app creates/edits them directly via IAM/Cognito APIs (see
    // infra/paid-groups.md) whenever a program or paid group is added/removed. If CDK owned
    // that content, every deploy would revert whatever the admin tools last wrote. So the
    // Free/Anon policies are referenced by ARN only below, and paid groups don't exist in
    // this stack at all — see the Identity Pool Role Attachments section for how the android
    // pool's role-mapping config is (deliberately) no longer a CDK-managed resource.
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

    // Free/Anon policy content is owned by the admin web app (see note above) —
    // reference by ARN only, never create/import as CDK-managed resources.
    const freePolicyArn = `arn:aws:iam::${this.account}:policy/DynamoDb-Refitted.Dev01-Free`;
    const anonPolicyArn = `arn:aws:iam::${this.account}:policy/DynamoDb-Refitted.Dev01-Anon`;

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

    // Ceiling for every android content role (Free, Auth/Anon, and every runtime-created
    // paid-group role) - grants nothing itself, just caps what those roles' own policies
    // can ever reach. See infra/paid-groups.md for why this matters for runtime-provisioned
    // paid-group roles specifically.
    const groupRoleBoundary = new iam.ManagedPolicy(this, 'GroupRoleBoundary', {
      managedPolicyName: 'Refitted-GroupRoleBoundary',
      statements: [new iam.PolicyStatement({
        actions: ['dynamodb:BatchGetItem', 'dynamodb:GetItem', 'dynamodb:Query'],
        resources: [tableArn, reverseIndexArn],
      })],
    });
    groupRoleBoundary.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    // Grants permission to update the Free/Anon policy versions, and every paid group's
    // policy (name-prefixed, since paid-group policies are created at runtime rather than
    // by this stack) - the web admin's updateGroupWorkouts server action edits these.
    const iamEditGroupsPolicy = new iam.ManagedPolicy(this, 'IamEditGroups', {
      managedPolicyName: 'IAM-Refitted-EditGroups',
      statements: [
        new iam.PolicyStatement({
          sid: 'VisualEditor0',
          actions: [
            'iam:GetPolicyVersion', 'iam:ListPolicyVersions',
            'iam:CreatePolicyVersion', 'iam:DeletePolicyVersion',
          ],
          resources: [`arn:aws:iam::${this.account}:policy/DynamoDb-Refitted.Dev01-*`],
        }),
        new iam.PolicyStatement({
          sid: 'ResolvePaidGroupPolicy',
          actions: ['iam:ListAttachedRolePolicies'],
          resources: [`arn:aws:iam::${this.account}:role/Cognito_refitted_*`],
        }),
        new iam.PolicyStatement({
          sid: 'ListPaidGroupRules',
          actions: ['cognito-identity:GetIdentityPoolRoles'],
          resources: [`arn:aws:cognito-identity:${this.region}:${this.account}:identitypool/${androidPool.ref}`],
        }),
      ],
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

    // Unauth role is AWS auto-generated at Identity Pool creation time; import by name.
    // (The android pool's unauthenticated role, Cognito_refitted_androidUnauth_Role, no
    // longer needs a CDK reference - its pool's role-mapping config is admin-owned live
    // data now; see infra/paid-groups.md.)
    const webUnauthRole = iam.Role.fromRoleName(this, 'WebUnauthRole', 'Cognito_refittedUnauth_Role');

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
      permissionsBoundary: groupRoleBoundary,
    });
    androidAuthRole.addManagedPolicy(anonPolicy);
    androidAuthRole.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);

    const androidFreeRole = new iam.Role(this, 'AndroidFreeRole', {
      roleName: 'Cognito_refitted_androidFree_Role',
      assumedBy: new iam.WebIdentityPrincipal('cognito-identity.amazonaws.com', {
        StringEquals: { 'cognito-identity.amazonaws.com:aud': androidPool.ref },
        'ForAnyValue:StringLike': { 'cognito-identity.amazonaws.com:amr': FIREBASE_OIDC_DOMAIN },
      }),
      permissionsBoundary: groupRoleBoundary,
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

    // The android pool's CfnIdentityPoolRoleAttachment is deliberately NOT a CDK-managed
    // resource. It used to be (base auth/unauth roles + a static free-email rule + one
    // Equals-<uuid> rule per paid group), but every paid group meant a CDK deploy to add
    // its rule. The attachment previously created here (construct id "AndroidPoolRoles")
    // was orphaned (RETAIN) rather than destroyed in the deploy that removed this code, so
    // the live rules/roles it created are untouched. The web admin app now owns this
    // resource's live state directly (GetIdentityPoolRoles / SetIdentityPoolRoles) - see
    // infra/paid-groups.md for the current rule shape and how a new paid group's role,
    // policy, and rule get created. Re-adding a CfnIdentityPoolRoleAttachment for this pool
    // here would clobber whatever the admin app has since written.
  }
}
