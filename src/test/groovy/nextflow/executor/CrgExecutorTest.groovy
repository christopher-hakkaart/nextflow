/*
 * Copyright (c) 2013-2014, Centre for Genomic Regulation (CRG).
 * Copyright (c) 2013-2014, Paolo Di Tommaso and the respective authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.executor

import java.nio.file.Paths

import nextflow.Session
import nextflow.processor.TaskConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.BaseScript
import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CrgExecutorTest extends Specification {

    @Unroll
    def 'test qsub cmd line' () {

        given:
        // mock process
        def proc = Mock(TaskProcessor)
        def base = Mock(BaseScript)
        def config = new TaskConfig(base)
        // LSF executor
        def executor = [:] as CrgExecutor
        executor.taskConfig = config
        executor.session = new Session()

        when:
        // process name
        proc.getName() >> 'task_x'
        // the script
        def script = Paths.get('.job.sh')
        // config
        config.queue = test_queue
        config.clusterOptions = test_opts
        config.name = 'task'
        config.penv = test_penv
        config.memory = test_mem
        config.time = test_time
        config.cpus = test_cpu
        executor.session.config.docker = [enabled: test_enabled]

        def task = new TaskRun()
        task.processor = proc
        task.workDir = Paths.get('/abc')
        task.index = 2
        task.container = test_container

        then:
        executor.getSubmitCommandLine(task,script).join(' ') == expected

        where:
        test_mem | test_time | test_cpu | test_penv | test_queue    | test_container | test_enabled | test_opts    || expected
        null    | null       |  null    | null      | 'short'       | null           | false        | null         || 'qsub -wd /abc -N nf-task_x_2 -o /dev/null -j y -terse -V -notify -q short .job.sh'
        null    | null       |  1       | null      | 'short'       | null           | false        | null         || 'qsub -wd /abc -N nf-task_x_2 -o /dev/null -j y -terse -V -notify -q short -l slots=1 .job.sh'
        null    | '10s '     |  1       | null      | 'long'        | null           | false        | '-extra opt '|| 'qsub -wd /abc -N nf-task_x_2 -o /dev/null -j y -terse -V -notify -q long -l slots=1 -l h_rt=00:00:10 -extra opt .job.sh'
        '1M'    | '10s '     |  1       | null      | 'long'        | null           | false        | null         || 'qsub -wd /abc -N nf-task_x_2 -o /dev/null -j y -terse -V -notify -q long -l slots=1 -l h_rt=00:00:10 -l virtual_free=1M .job.sh'
        '2 M'   | '2 m'      | '1'      | 'smp'     | 'www'         | null           | false        | null         || 'qsub -wd /abc -N nf-task_x_2 -o /dev/null -j y -terse -V -notify -q www -pe smp 1 -l h_rt=00:02:00 -l virtual_free=2M .job.sh'
        '3 g'   | '3 d'      | '2'      | 'mpi'     | 'www'         | null           | false        | null         || 'qsub -wd /abc -N nf-task_x_2 -o /dev/null -j y -terse -V -notify -q www -pe mpi 2 -l h_rt=72:00:00 -l virtual_free=3G .job.sh'
        '4 GB ' | '1d3h'     | '4'      | 'orte'    | 'alpha'       | null           | false        | null         || 'qsub -wd /abc -N nf-task_x_2 -o /dev/null -j y -terse -V -notify -q alpha -pe orte 4 -l h_rt=27:00:00 -l virtual_free=4G .job.sh'
        null    | null       |  null    | null      | 'alpha'       | 'ubuntu'       | false        | null         || 'qsub -wd /abc -N nf-task_x_2 -o /dev/null -j y -terse -V -notify -q alpha .job.sh'
        null    | null       |  null    | null      | 'alpha'       | 'ubuntu:latest'| true         | null         || 'qsub -wd /abc -N nf-task_x_2 -o /dev/null -j y -terse -V -notify -q alpha -soft -l docker_images=ubuntu:latest -hard .job.sh'
        '3 g'   | '3 d'      | '2'      | 'mpi'     | 'alpha'       | 'busybox'      | true         | '-a 1 -b 2'  || 'qsub -wd /abc -N nf-task_x_2 -o /dev/null -j y -terse -V -notify -q alpha -pe mpi 2 -l h_rt=72:00:00 -l virtual_free=3G -soft -l docker_images=busybox -hard -a 1 -b 2 .job.sh'

    }


    def testParseJobId() {

        when:
        def executor = [:] as CrgExecutor
        def textToParse = '''
            blah blah ..
            .. blah blah
            6472
            '''
        then:
        executor.parseJobId(textToParse) == '6472'
    }

    def testKillTaskCommand() {

        when:
        def executor = [:] as CrgExecutor
        then:
        executor.killTaskCommand(123) == ['qdel', '-j', '123'] as String[]

    }


    def testParseQueueStatus() {

        setup:
        def executor = [:] as CrgExecutor
        def text =
        """
        job-ID  prior   name       user         state submit/start at     queue                          slots ja-task-ID
        -----------------------------------------------------------------------------------------------------------------
        7548318 0.00050 nf-exonera pditommaso   r     02/10/2014 12:30:51 long@node-hp0214.linux.crg.es      1
        7548348 0.00050 nf-exonera pditommaso   r     02/10/2014 12:32:43 long@node-hp0204.linux.crg.es      1
        7548349 0.00050 nf-exonera pditommaso   hqw   02/10/2014 12:32:56 long@node-hp0303.linux.crg.es      1
        7548904 0.00050 nf-exonera pditommaso   qw    02/10/2014 13:07:09                                    1
        7548960 0.00050 nf-exonera pditommaso   Eqw   02/10/2014 13:08:11                                    1
        """.stripIndent().trim()


        when:
        def result = executor.parseQueueStatus(text)
        then:
        result.size() == 5
        result['7548318'] == AbstractGridExecutor.QueueStatus.RUNNING
        result['7548348'] == AbstractGridExecutor.QueueStatus.RUNNING
        result['7548349'] == AbstractGridExecutor.QueueStatus.HOLD
        result['7548904'] == AbstractGridExecutor.QueueStatus.PENDING
        result['7548960'] == AbstractGridExecutor.QueueStatus.ERROR

    }

    def testParseQueueStatusFromUniva() {
        setup:
        def executor = [:] as CrgExecutor
        def text =
        """
        job-ID     prior   name       user         state submit/start at     queue                          jclass                         slots ja-task-ID
        ------------------------------------------------------------------------------------------------------------------------------------------------
              1220 1.00000 oliver-tes abria        r     08/29/2014 10:17:21 long@node-hp0115.linux.crg.es                                     8
              1258 0.17254 mouse.4689 epalumbo     r     08/29/2014 11:13:55 long@node-hp0515.linux.crg.es                                    16
              1261 0.17254 run_mappin epalumbo     qw    08/29/2014 11:28:11 short@node-ib0208bi.linux.crg.                                    4
              1262 0.17254 run_mappin epalumbo     Eqw   08/29/2014 11:28:31 short@node-ib0209bi.linux.crg.                                    4
        """.stripIndent().trim()


        when:
        def result = executor.parseQueueStatus(text)
        then:
        result.size() == 4
        result['1220'] == AbstractGridExecutor.QueueStatus.RUNNING
        result['1258'] == AbstractGridExecutor.QueueStatus.RUNNING
        result['1261'] == AbstractGridExecutor.QueueStatus.PENDING
        result['1262'] == AbstractGridExecutor.QueueStatus.ERROR
    }

    def testParseQueueDump() {

        setup:
        def executor = [:] as CrgExecutor
        def text =
                """
        job-ID  prior   name       user         state submit/start at     queue                          slots ja-task-ID
        -----------------------------------------------------------------------------------------------------------------
        7548318 0.00050 nf-exonera pditommaso   r     02/10/2014 12:30:51 long@node-hp0214.linux.crg.es      1
        7548348 0.00050 nf-exonera pditommaso   r     02/10/2014 12:32:43 long@node-hp0204.linux.crg.es      1
        7548349 0.00050 nf-exonera pditommaso   hqw   02/10/2014 12:32:56 long@node-hp0303.linux.crg.es      1
        7548904 0.00050 nf-exonera pditommaso   qw    02/10/2014 13:07:09                                    1
        7548960 0.00050 nf-exonera pditommaso   Eqw   02/10/2014 13:08:11                                    1
        """.stripIndent().trim()


        when:
        executor.fQueueStatus = executor.parseQueueStatus(text)
        then:
        executor.dumpQueueStatus().readLines().sort() == [
                '  job: 7548318: RUNNING',
                '  job: 7548348: RUNNING',
                '  job: 7548349: HOLD',
                '  job: 7548904: PENDING',
                '  job: 7548960: ERROR'
        ]


    }

    def testQueueStatusCommand() {

        setup:
        def executor = [:] as CrgExecutor

        expect:
        executor.queueStatusCommand(null) == ['qstat']
        executor.queueStatusCommand('long') == ['qstat','-q','long']

    }

}
