# Before running the code below, please follow these steps to setup your workspace if you have not
# set it up already:
#
# 1. Setup credentials for DynamoDB access. One of the ways to setup credentials is to add them to
#    ~/.aws/credentials file (C:\Users\USER_NAME\.aws\credentials file for Windows users) in
#    following format:
#
#    [<profile_name>]
#    aws_access_key_id = YOUR_ACCESS_KEY_ID
#    aws_secret_access_key = YOUR_SECRET_ACCESS_KEY
#
#    If <profile_name> is specified as "default" then AWS SDKs and CLI will be able to read the credentials
#    without any additional configuration. But if a different profile name is used then it needs to be
#    specified while initializing DynamoDB client via AWS SDKs or while configuring AWS CLI.
#    Please refer following guide for more details on credential configuration:
#    https://boto3.amazonaws.com/v1/documentation/api/latest/guide/quickstart.html#configuration
#
# 2. Install the latest Boto 3 release via pip:
#
#    pip install boto3
#
#    Please refer following guide for more details on Boto 3 installation:
#    https://boto3.amazonaws.com/v1/documentation/api/latest/guide/quickstart.html#installation
#    Please note that you may need to follow additional setup steps for using Boto 3 from an IDE. Refer
#    your IDE's documentation if you run into issues.


# Load the AWS SDK for Python
import boto3
from botocore.exceptions import ClientError

ERROR_HELP_STRINGS = {
    # Operation specific errors
    'ConditionalCheckFailedException': 'Condition check specified in the operation failed, review and update the condition check before retrying',
    'TransactionConflictException': 'Operation was rejected because there is an ongoing transaction for the item, generally safe to retry with exponential back-off',
    'ItemCollectionSizeLimitExceededException': 'An item collection is too large, you\'re using Local Secondary Index and exceeded size limit of items per partition key.' +
                                                ' Consider using Global Secondary Index instead',
    # Common Errors
    'InternalServerError': 'Internal Server Error, generally safe to retry with exponential back-off',
    'ProvisionedThroughputExceededException': 'Request rate is too high. If you\'re using a custom retry strategy make sure to retry with exponential back-off.' +
                                              'Otherwise consider reducing frequency of requests or increasing provisioned capacity for your table or secondary index',
    'ResourceNotFoundException': 'One of the tables was not found, verify table exists before retrying',
    'ServiceUnavailable': 'Had trouble reaching DynamoDB. generally safe to retry with exponential back-off',
    'ThrottlingException': 'Request denied due to throttling, generally safe to retry with exponential back-off',
    'UnrecognizedClientException': 'The request signature is incorrect most likely due to an invalid AWS access key ID or secret key, fix before retrying',
    'ValidationException': 'The input fails to satisfy the constraints specified by DynamoDB, fix input before retrying',
    'RequestLimitExceeded': 'Throughput exceeds the current throughput limit for your account, increase account level throughput before retrying',
}

# Use the following function instead when using DynamoDB Local
#def create_dynamodb_client(region):
#    return boto3.client("dynamodb", region_name="localhost", endpoint_url="http://localhost:8000", aws_access_key_id="access_key_id", aws_secret_access_key="secret_access_key")

def create_dynamodb_client(region="us-east-1"):
    return boto3.client("dynamodb", region_name=region)


def create_put_item_input():
    return {
        "TableName": "refitted.dev01",
        "Item": {
            "Id": {"S":"1.0"},
            "Disc": {"S":"X"},
            "Reps": {"N":"1"},
            "ToFailure": {"BOOL": True}
        }
    }

def create_batch_write_input():
    items = [
        {
            "Id": {"S":"1.0"},
            "Disc": {"S":"X"},
            "Reps": {"N":"1"},
            "ToFailure": {"BOOL": True}
        }
    ]
    n = 25
    chunks = [items[i:i + n] for i in range(0, len(items), n)]
    return [{
        "refitted.dev01": [{
            'PutRequest': {
                'Item': item
            }
        } for item in chunk]
    } for chunk in chunks]


def execute_put_item(dynamodb_client, input):
    try:
        response = dynamodb_client.put_item(**input)
        print("Successfully put item.")
        # Handle response
    except ClientError as error:
        handle_error(error)
    except BaseException as error:
        print("Unknown error while putting item: " + error.response['Error']['Message'])

def execute_batch_write(dynamodb_client, input):
    for batch in input:
        try:
            response = dynamodb_client.batch_write_item(RequestItems=batch)
            print("Successfully wrote batch.")
            # Handle response
        except ClientError as error:
            handle_error(error)
        except BaseException as error:
            print("Unknown error while writing batch: " + error.response['Error']['Message'])


def handle_error(error):
    error_code = error.response['Error']['Code']
    error_message = error.response['Error']['Message']

    error_help_string = ERROR_HELP_STRINGS[error_code]

    print('[{error_code}] {help_string}. Error message: {error_message}'
          .format(error_code=error_code,
                  help_string=error_help_string,
                  error_message=error_message))


def main():
    # Create the DynamoDB Client with the region you want
    dynamodb_client = create_dynamodb_client(region="us-east-2")

    # Create the dictionary containing arguments for put_item call
    # put_item_input = create_put_item_input()
    batch_write_input = create_batch_write_input()

    # Call DynamoDB's put_item API
    # execute_put_item(dynamodb_client, put_item_input)
    execute_batch_write(dynamodb_client, batch_write_input)


if __name__ == "__main__":
    main()
