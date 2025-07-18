/*
 * Copyright 2024-2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nextflow.config.scopes;

import nextflow.config.schema.ConfigOption;
import nextflow.config.schema.ConfigScope;
import nextflow.script.dsl.Description;
import nextflow.script.types.Duration;
import nextflow.script.types.MemoryUnit;

public class AwsClientConfig implements ConfigScope {

    @ConfigOption
    @Description("""
        Allow the access of public S3 buckets without providing AWS credentials. Any service that does not accept unsigned requests will return a service access error.
    """)
    public boolean anonymous;

    @ConfigOption
    @Description("""
        Specify predefined bucket permissions, also known as *canned ACL*. Can be one of `Private`, `PublicRead`, `PublicReadWrite`, `AuthenticatedRead`, `LogDeliveryWrite`, `BucketOwnerRead`, `BucketOwnerFullControl`, or `AwsExecRead`.
        
        [Read more](https://docs.aws.amazon.com/AmazonS3/latest/userguide/acl-overview.html#canned-acl)
    """)
    public String s3Acl;

    @ConfigOption
    @Description("""
        The amount of time to wait (in milliseconds) when initially establishing a connection before timing out.
    """)
    public int connectionTimeout;

    @ConfigOption
    @Description("""
        The AWS S3 API entry point e.g. `https://s3-us-west-1.amazonaws.com`. The endpoint must include the protocol prefix e.g. `https://`.
    """)
    public String endpoint;

    @ConfigOption
    @Description("""
        The maximum number of concurrency in S3 async clients.
    """)
    public int maxConcurrency;

    @ConfigOption
    @Description("""
        The maximum number of allowed open HTTP connections.
    """)
    public int maxConnections;

    @ConfigOption
    @Description("""
        The maximum number of retry attempts for failed retryable requests.
    """)
    public int maxErrorRetry;

    @ConfigOption
    @Description("""
        The maximum native memory used by the S3 asynchronous client for S3 transfers.
    """)
    public MemoryUnit maxNativeMemory;

    @ConfigOption
    @Description("""
        The minimum size of a single part in a multipart upload (default: `8 MB`).
    """)
    public MemoryUnit minimumPartSize;

    @ConfigOption
    @Description("""
        The S3 Async client threshold to create multipart S3 transfers. Default is the same as `minimumPartSize`.
    """)
    public MemoryUnit multipartThreshold;

    @ConfigOption
    @Description("""
        The proxy host to connect through.
    """)
    public String proxyHost;

    @ConfigOption
    @Description("""
        The port on the proxy host to connect through.
    """)
    public int proxyPort;

    @ConfigOption
    @Description("""
        The protocol scheme to use when connecting through a proxy (http/https).
    """)
    public String proxyScheme;

    @ConfigOption
    @Description("""
        The user name to use when connecting through a proxy.
    """)
    public String proxyUsername;

    @ConfigOption
    @Description("""
        The password to use when connecting through a proxy.
    """)
    public String proxyPassword;

    @ConfigOption
    @Description("""
        Enable the requester pays feature for S3 buckets.
    """)
    public boolean requesterPays;

    @ConfigOption
    @Description("""
        Enable the use of path-based access model that is used to specify the address of an object in S3-compatible storage systems.
    """)
    public boolean s3PathStyleAccess;

    @ConfigOption
    @Description("""
        The amount of time to wait (in milliseconds) for data to be transferred over an established, open connection before the connection is timed out.
    """)
    public int socketTimeout;

    @ConfigOption
    @Description("""
        The S3 server side encryption to be used when saving objects on S3, either `AES256` or `aws:kms` values are allowed.
    """)
    public String storageEncryption;

    @ConfigOption
    @Description("""
        The AWS KMS key Id to be used to encrypt files stored in the target S3 bucket.
    """)
    public String storageKmsKeyId;

    @ConfigOption
    @Description("""
        The S3 Async client target network throughput in Gbps. This value is used to automatically set `maxConcurrency` and `maxNativeMemory` (default: `10`).
    """)
    public Double targetThroughputInGbps;

    @ConfigOption
    @Description("""
        The number of threads used by the S3 transfer manager (default: `10`).
    """)
    public int transferManagerThreads;

    @ConfigOption
    @Description("""
        The S3 storage class applied to stored objects, one of \\[`STANDARD`, `STANDARD_IA`, `ONEZONE_IA`, `INTELLIGENT_TIERING`\\] (default: `STANDARD`).
    """)
    public String uploadStorageClass;

}
