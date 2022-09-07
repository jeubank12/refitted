import { EndpointBuilder } from "@reduxjs/toolkit/dist/query/endpointDefinitions";
import { BaseQueryFn } from "@reduxjs/toolkit/dist/query/react";

export type AwsBaseQuery = BaseQueryFn

export const awsPath = 'aws'

type AwsTagTypes = never

export type AwsEndpointBuilder = EndpointBuilder<AwsBaseQuery, AwsTagTypes, typeof awsPath>
