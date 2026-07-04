import * as cdk from 'aws-cdk-lib';
import { DatabaseStack } from '../lib/database-stack';
import { AuthStack } from '../lib/auth-stack';

const app = new cdk.App();

const env: cdk.Environment = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: 'us-east-2',
};

const db = new DatabaseStack(app, 'RefittedDatabase', { env });
new AuthStack(app, 'RefittedAuth', { env, table: db.table });
