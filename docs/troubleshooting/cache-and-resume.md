(cache-failers-page)=

# Cache failures

Cache failures occur when a task that was supposed to be cached was re-executed or a task that was supposed to be re-executed was cached.

Common reasons for cache failures include:

- Modified inputs
- The `-resume` option not being enabled
- Non-default {ref}`process-cache` directives
- Files in the task cache and work directory being deleted, moved, or edited

This page provides an overview of common causes for cache failures and strategies to identify and resolve them.

(troubleshooting-modified)=

## Modified inputs

Modifying inputs that are used in the task hash will invalidate the cache. Common causes of modified inputs include:

- Changing input files
- Resuming from a different session ID
- Changing the process name
- Changing the task container image or Conda environment
- Changing the task script
- Changing a bundled script used by the task

:::{note}
Changing the value of any directive, except {ref}`process-ext`, will not inactivate the task cache.
:::

A hash for an input file is calculated from the complete file path, the last modified timestamp, and the file size to calculate. If any of these attributes change the task will be re-executed. If a process modifies its input files it cannot be resumed. Processes that modify their own input files are considered to be an anti-pattern and should be avoided.

(troubleshooting-inconsistent)=

## Inconsistent file attributes

Some shared file systems, such as NFS, may report inconsistent file timestamps. If you encounter this problem, use the `'lenient'` {ref}`caching mode <process-cache>` to ignore the last modified timestamp and use only the file path.

(troubleshooting-race-condition)=

## Race condition on a global variable

Race conditions can in disrupt caching behavior of your pipeline.

<h3>Problem example</h3>

```nextflow
Channel.of(1,2,3) | map { v -> X=v; X+=2 } | view { v -> "ch1 = $v" }
Channel.of(1,2,3) | map { v -> X=v; X*=2 } | view { v -> "ch2 = $v" }
```

In the above example, `X` is declared in each `map` closure. Without the `def` keyword, or other type qualifier, the variable `X` is global to the entire script. Operators and executed concurrently and, as `X` is global, there is a *race condition* that causes the emitted values to vary depending on the order of the concurrent operations. If these values were passed to a process as inputs the process would execute different tasks during each run due to the race condition.

<h3>Solution</h3>

Ensure the variable is not global by using a local variable:
    
```nextflow
Channel.of(1,2,3) | map { v -> def X=v; X+=2 } | view { v -> "ch1 = $v" }
```

Alternatively, remove the variable:

    ```nextflow
    Channel.of(1,2,3) | map { v -> v * 2 } | view { v -> "ch2 = $v" }
    ```

(cache-nondeterministic-inputs)=

## Non-deterministic process inputs

A process that merges inputs from different sources non-deterministically may invalidate the cache.

<h3>Problem example</h3>

```nextflow
workflow {
    ch_foo = Channel.of( ['1', '1.foo'], ['2', '2.foo'] )
    ch_bar = Channel.of( ['2', '2.bar'], ['1', '1.bar'] )
    gather(ch_foo, ch_bar)
}

process gather {
    input:
    tuple val(id), file(foo)
    tuple val(id), file(bar)

    script:
    """
    merge_command $foo $bar
    """
}
```

In the above example, the inputs will be merged without matching. This is the same way method used by the {ref}`operator-merge` operator. When merged, the inputs are incorrect, non-deterministic, and invalidate the cache.

<h3>Solution</h3>

Ensure channels are deterministic by joining them before invoking the process:

```nextflow
workflow {
    ch_foo = Channel.of( ['1', '1.foo'], ['2', '2.foo'] )
    ch_bar = Channel.of( ['2', '2.bar'], ['1', '1.bar'] )
    gather(ch_foo.join(ch_bar))
}

process gather {
    input:
    tuple val(id), file(foo), file(bar)

    script:
    """
    merge_command $foo $bar
    """
}
```

## Compare task hashes

By identifying differences between hashes you can detect changes that may be causing cache failures.

To compare the task hashes for a resumed run:

1. Run your pipeline with the `-log` and `-dump-hashes` options:

    ```bash
    nextflow -log run_initial.log run <pipeline> -dump-hashes
    ```

2. Run your pipeline with the `-log`, `-dump-hashes`, and `-resume` options:

    ```bash
    nextflow -log run_resumed.log run <pipeline> -dump-hashes -resume
    ```

3. Extract the task hash lines from each log:

    ```bash
    cat run_initial.log | grep 'INFO.*TaskProcessor.*cache hash' | cut -d ' ' -f 10- | sort | awk '{ print; print ""; }' > run_initial.tasks.log
    cat run_resumed.log | grep 'INFO.*TaskProcessor.*cache hash' | cut -d ' ' -f 10- | sort | awk '{ print; print ""; }' > run_resumed.tasks.log
    ```

4. Compare the runs:

    ```bash
    diff run_initial.tasks.log run_resumed.tasks.log
    ```

    :::{tip}
    You can also compare the hash lines using a graphical diff viewer.
    :::

By comparing these hashes, you can identify which tasks have changed between runs and potentially understand why certain tasks are being re-executed instead of using cached results.

:::{versionadded} 23.10.0
:::

When using `-dump-hashes json`, the task hashes can be more easily extracted into a diff. Here is an example Bash script to perform two runs and produce a diff:

```bash
nextflow -log run_1.log run $pipeline -dump-hashes json
nextflow -log run_2.log run $pipeline -dump-hashes json -resume

get_hashes() {
    cat $1 \
    | grep 'cache hash:' \
    | cut -d ' ' -f 10- \
    | sort \
    | awk '{ print; print ""; }'
}

get_hashes run_1.log > run_1.tasks.log
get_hashes run_2.log > run_2.tasks.log

diff run_1.tasks.log run_2.tasks.log
```
