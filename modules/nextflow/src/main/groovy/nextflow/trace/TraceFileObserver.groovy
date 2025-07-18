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

package nextflow.trace

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import groovyx.gpars.agent.Agent
import nextflow.Session
import nextflow.processor.TaskHandler
import nextflow.processor.TaskId
import nextflow.trace.event.TaskEvent
import nextflow.util.TestOnly
/**
 * Create a CSV file containing the processes execution information
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class TraceFileObserver implements TraceObserverV2 {

    public static final String DEF_FILE_NAME = "trace-${TraceHelper.launchTimestampFmt()}.txt"

    /**
     * The list of fields included in the trace report
     */
    List<String> fields = [
            'task_id',
            'hash',
            'native_id',
            'name',
            'status',
            'exit',
            'submit',
            'duration',
            'realtime',
            '%cpu',
            'peak_rss',
            'peak_vmem',
            'rchar',
            'wchar'
    ]

    List<String> formats

    /**
     * The delimiter character used to separate column in the CSV file
     */
    protected String separator = '\t'

    /**
     * Overwrite existing trace file (required in some cases, as rolling filename has been deprecated)
     */
    protected boolean overwrite

    /**
     * The path where the file is created. It is set by the object constructor
     */
    private Path tracePath

    /**
     * The actual file object
     */
    private PrintWriter traceFile

    /**
     * Holds the the start time for tasks started/submitted but not yet completed
     */
    @PackageScope Map<TaskId,TraceRecord> current = new ConcurrentHashMap<>()

    private Agent<PrintWriter> writer

    private boolean useRawNumber

    void setFields( List<String> entries ) {

        final names = TraceRecord.FIELDS.keySet()
        final result = new ArrayList<String>(entries.size())
        for( final item : entries ) {
            final thisName = item.trim()

            if( thisName ) {
                if( thisName in names )
                    result << thisName
                else {
                    String message = "Not a valid trace field name: '$thisName'"
                    final alternatives = names.bestMatches(thisName)
                    if( alternatives )
                        message += " -- Possible solutions: ${alternatives.join(', ')}"
                    throw new IllegalArgumentException(message)
                }
            }

        }

        this.fields = result
    }

    TraceFileObserver setFieldsAndFormats( value ) {
        List<String> entries
        if( value instanceof String ) {
            entries = value.tokenize(', ')
        }
        else if( value instanceof List ) {
            entries = (List)value
        }
        else {
            throw new IllegalArgumentException("Not a valid trace fields value: $value")
        }

        List<String> fields = new ArrayList<>(entries.size())
        List<String> formats = new ArrayList<>(entries.size())

        for( String x : entries ) {
            String name
            String fmt
            int p = x.indexOf(':')
            if( p == -1 ) {
                name = x
                fmt = TraceRecord.FIELDS.get(name)      // get the default type
            }
            else {
                name = x.substring(0,p)
                fmt = x.substring(p+1)
            }

            if( !fmt )
                throw new IllegalArgumentException("Unknown trace field name: `$name`")

            if( useRawNumber && fmt in TraceRecord.NON_PRIMITIVE_TYPES ) {
                fmt = 'num'
            }

            fields << name.trim()
            formats << fmt.trim()
        }

        setFields(fields)
        setFormats(formats)

        return this
    }

    TraceFileObserver useRawNumbers( Boolean value ) {
        this.useRawNumber = value

        List<String> local = []
        for( String name : fields ) {
            def type = TraceRecord.FIELDS.get(name)
            if( useRawNumber && type in TraceRecord.NON_PRIMITIVE_TYPES ) {
                type = 'num'
            }
            local << type
        }
        this.formats = local
        return this
    }

    /**
     * Create the trace observer
     *
     * @param traceFile A path to the file where save the tracing data
     */
    TraceFileObserver(Path traceFile, Boolean overwrite=false, String separator='\t') {
        this.tracePath = traceFile
        this.overwrite = overwrite
        this.separator = separator
    }

    @TestOnly
    protected TraceFileObserver() {}

    /**
     * Create the trace file, in file already existing with the same name it is
     * "rolled" to a new file
     */
    @Override
    void onFlowCreate(Session session) {
        log.debug "Workflow started -- trace file: ${tracePath.toUriString()}"

        // make sure parent path exists
        final parent = tracePath.getParent()
        if( parent )
            Files.createDirectories(parent)

        // create a new trace file
        traceFile = new PrintWriter(TraceHelper.newFileWriter(tracePath,overwrite, 'Trace'))

        // launch the agent
        writer = new Agent<PrintWriter>(traceFile)
        writer.send { traceFile.println(fields.join(separator)); traceFile.flush() }
    }

    /**
     * Save the pending processes and close the trace file
     */
    @Override
    void onFlowComplete() {
        log.debug "Workflow completed -- saving trace file"

        // wait for termination and flush the agent content
        writer.await()

        // write the remaining records
        current.values().each { record -> traceFile.println(render(record)) }
        traceFile.flush()
        traceFile.close()
    }

    @Override
    void onTaskSubmit(TaskEvent event) {
        current[ event.trace.taskId ] = event.trace
    }

    @Override
    void onTaskStart(TaskEvent event) {
        current[ event.trace.taskId ] = event.trace
    }

    @Override
    void onTaskComplete(TaskEvent event) {
        final taskId = event.handler.task.id
        if( !event.trace ) {
            log.debug "[WARN] Unable to find record for task run with id: ${taskId}"
            return
        }

        // remove the record from the current records
        current.remove(taskId)

        // save to the file
        writer.send { PrintWriter it ->
            it.println(render(event.trace))
            it.flush()
        }
    }

    @Override
    void onTaskCached(TaskEvent event) {
        // event was triggered by a stored task, ignore it
        if( !event.trace ) {
            return
        }

        // save to the file
        writer.send { PrintWriter it ->
            it.println(render(event.trace))
            it.flush()
        }
    }

    /**
     * Render a {@link TraceRecord} object to a string
     *
     * @param trace
     * @return
     */
    String render(TraceRecord trace) {
        assert trace
        trace.renderText(fields, formats, separator)
    }

    @Override
    boolean enableMetrics() {
        return true
    }
}
