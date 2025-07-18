/*
 * Copyright 2013-2024, Seqera Labs
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

package nextflow.cloud.aws.batch

import software.amazon.awssdk.services.batch.BatchClient
import nextflow.util.ClientProxyThrottler
import nextflow.util.ThrottlingExecutor
/**
 * Implements a AWS Batch client proxy that handle all API invocations
 * through the provided executor service
 *
 * WARN: the caller class/method should not be compile static
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AwsBatchProxy extends ClientProxyThrottler<BatchClient> {

    @Delegate(deprecated=true)
    private BatchClient target

    AwsBatchProxy(BatchClient client, ThrottlingExecutor executor) {
        super(client, executor, [describeJobs: 10 as Byte]) // note: use higher priority for `describeJobs` invocations
        this.target = client
    }

}
