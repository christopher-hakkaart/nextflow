(workflow-page)=

# Workflows

A **workflow** composes {ref}`processes <process-page>` and dataflow logic to define how data flows through your pipeline. A Nextflow script typically includes:

- **[Parameters](#parameters)** - Configurable inputs
- **[Entry workflow](#entry-workflow)** - Main entrypoint that orchestrates the pipeline
- **[Named workflows](#named-workflows)** - Reusable workflow components that can be called by other workflows
- **[Dataflow](#dataflow)** - Channels and operators connecting processes
- **[Outputs](#outputs)** - Published results

For detailed syntax and usage instructions, see {ref}`syntax-workflow`.

(workflow-params-def)=

## Parameters

Parameters are configurable variables that control pipeline behavior. You can declare parameters with [typed parameters](#typed-parameters) in the `params` block or with [legacy parameters](#legacy-parameters) to customize pipeline behavior at runtime.

### Typed parameters

:::{versionadded} 25.10.0
:::

:::{note}
Typed parameters require the {ref}`strict syntax <strict-syntax-page>`. Set the `NXF_SYNTAX_PARSER` environment variable to `v2` to enable:

```bash
export NXF_SYNTAX_PARSER=v2
```
:::

Typed parameters are parameters with explicit type annotations that ensure type safety and provide validation at runtime. You can declare typed parameters using the `params` block:

```nextflow
params {
    // Path to input data.
    input: Path

    // Whether to save intermediate files.
    save_intermeds: Boolean = false
}
```

The following parameter types are available:

- {ref}`stdlib-types-boolean`
- {ref}`stdlib-types-float`
- {ref}`stdlib-types-integer`
- {ref}`stdlib-types-path`
- {ref}`stdlib-types-string`

You can use parameters in the entry workflow:

```nextflow
workflow {
    analyze(params.input, params.save_intermeds)
}
```

:::{note}
Parameters should be referenced only in the entry workflow or `output` block. Pass parameters to workflows and processes as explicit inputs.
:::

You can override default parameter values by the command line, params file, or config file. Nextflow resolves parameters from multiple sources in the order described in {ref}`cli-params`. Nextflow converts parameters specified on the command line to the appropriate type based on its type annotation.

A parameter that doesn't specify a default value is a *required parameter*. If you do not supply a value for a required parameter at runtime, the run will fail.

(workflow-params-legacy)=

### Legacy parameters

Legacy parameters are the traditional untyped parameter declaration style that uses property assignment. You can declare legacy parameters by assigning a `params` property to a default value:

```nextflow
params.input = '/some/data/file'
params.save_intermeds = false

workflow {
    if( params.input )
        analyze(params.input, params.save_intermeds)
    else
        analyze(fake_input(), params.save_intermeds)
}
```

You can override default values by using the command line, a params file, or a config file. See {ref}`cli-params`  for more information about how parameters from multiple sources are resolved.

## Entry workflow

The *entry workflow* is the unnamed workflow that serves as the entry point of your script. Each script can have up to one unnamed entry workflow:

```nextflow
workflow {
    channel.of('Bonjour', 'Ciao', 'Hello', 'Hola')
        .map { v -> "$v world!" }
        .view()
}
```

(workflow-output-def)=

## Outputs

:::{versionadded} 25.10.0
Workflow outputs are available as a preview in Nextflow {ref}`24.04 <workflow-outputs-first-preview>`, {ref}`24.10 <workflow-outputs-second-preview>`, and {ref}`25.04 <workflow-outputs-third-preview>`.
:::

:::{note}
Workflow outputs are intended to replace the {ref}`publishDir <process-publishdir>` directive. See {ref}`migrating-workflow-outputs` for guidance on migrating from `publishDir` to workflow outputs.
:::

Workflow outputs define the final results that a pipeline publishes when execution completes. You can define an *output block* to declare your workflow's top-level outputs. Assign outputs in the `publish` section of the entry workflow. You can assign any channel, including process and subworkflow outputs.

**Example:**

```nextflow
process fetch {
    // ...

    output:
    path 'sample.txt'

    // ...
}

workflow {
    main:
    ch_samples = fetch(params.input)

    publish:
    // Assign ch_samples to the samples workflow output
    samples = ch_samples
}

output {
    // Assign ch_samples to the samples workflow output
    samples {
        path '.'
    }
}
```

(workflow-publishing-files)=

### Publishing files

Publishing files is the process of copying or linking output files from the work directory to a designated output directory. You can set the top-level output directory for a run using the `-output-dir` command-line option or the `outputDir` config option:

```bash
nextflow run main.nf -output-dir 'my-results'
```

```groovy
// nextflow.config
outputDir = 'my-results'
```

The default output directory is `results` in the launch directory.

By default, Nextflow publishes all output files to the output directory. Each output in the output block can define where Nextflow publishes files using the `path` directive:

```nextflow
workflow {
    main:
    ch_step1 = step1()
    ch_step2 = step2(ch_step1)

    publish:
    step1 = ch_step1
    step2 = ch_step2
}

output {
    step1 {
        path 'step1'
    }
    step2 {
        path 'step2'
    }
}
```

The following directory structure is created:

```
results/
└── step1/
    └── ...
└── step2/
    └── ...
```

Nextflow publishes all files received by an output into the specified directory. Nextflow recursively scans lists, maps, and tuples for nested files:

```nextflow
workflow {
    main:
    ch_samples = channel.of(
        tuple( [id: 'SAMP1'], [ file('1.txt'), file('2.txt') ] )
    )

    publish:
    samples = ch_samples // 1.txt and 2.txt are published
}
```

The `path` directive can also be a closure that defines a custom publish path for each channel value:

```nextflow
workflow {
    main:
    ch_samples = channel.of(
        [id: 'SAMP1', fastq_1: file('1.fastq'), fastq_2: file('2.fastq')]
    )

    publish:
    samples = ch_samples
}

output {
    samples {
        // Publish pairs of FASTQ files to subdirectory based on sample ID
        path { sample -> "fastq/${sample.id}/" }
    }
}
```

You can define a different path for each individual file using the `>>` operator:

```nextflow
output {
    samples {
        path { sample ->
            sample.fastq_1 >> "fastq/${sample.id}/"
            sample.fastq_2 >> "fastq/${sample.id}/"
        }
    }
}
```

Each `>>` specifies a *source file* and *publish target*. The source file should be a file or collection of files, and the publish target should be a directory or file name. If the publish target ends with a slash, Nextflow treats it as the directory in which to publish source files.

:::{note}
Nextflow does not publish files that do not originate from the work directory.
:::

### Index files

Index files are structured metadata files (CSV, JSON, or YAML) that catalog published outputs along with their associated metadata. An index file preserves the structure of channel values, including metadata, which is simpler than encoding this information with directories and file names. The index file can be a CSV (`.csv`), JSON (`.json`), or YAML (`.yml`, `.yaml`) file. The channel values should be files, lists, maps, or tuples.

For example:

```nextflow
workflow {
    main:
    ch_samples = channel.of(
        [id: 1, name: 'sample 1', fastq_1: '1a.fastq', fastq_2: '1b.fastq'],
        [id: 2, name: 'sample 2', fastq_1: '2a.fastq', fastq_2: '2b.fastq'],
        [id: 3, name: 'sample 3', fastq_1: '3a.fastq', fastq_2: null]
    )

    publish:
    samples = ch_samples
}

output {
    samples {
        path 'fastq'
        index {
            path 'samples.csv'
        }
    }
}
```

This example writes the following CSV file to `results/samples.csv`:

```
"1","sample 1","results/fastq/1a.fastq","results/fastq/1b.fastq"
"2","sample 2","results/fastq/2a.fastq","results/fastq/2b.fastq"
"3","sample 3","results/fastq/3a.fastq",""
```

You can customize the index file with additional directives, for example:

```nextflow
index {
    path 'samples.csv'
    header true
    sep '|'
}
```

This example produces the following index file:

```
"id"|"name"|"fastq_1"|"fastq_2"
"1"|"sample 1"|"results/fastq/1a.fastq"|"results/fastq/1b.fastq"
"2"|"sample 2"|"results/fastq/2a.fastq"|"results/fastq/2b.fastq"
"3"|"sample 3"|"results/fastq/3a.fastq"|""
```

:::{note}
Nextflow does not publish files that do not originate from the work directory, but includes them in the index file.
:::

See {ref}`outputs-page` for the reference list of index directives.

## Named workflows

A *named workflow* is a workflow that can be called by other workflows:

```nextflow
workflow my_workflow {
    hello()
    bye( hello.out.collect() )
}

workflow {
    my_workflow()
}
```

This example defines a workflow named `my_workflow` that is called by the entry workflow. Both `hello` and `bye` could be any other process or workflow.

### Takes and emits

Takes and emits are named workflow interface declarations that define workflow inputs and outputs respectively. The `take:` section declares the inputs of a named workflow:

```nextflow
workflow my_workflow {
    take:
    data1
    data2

    main:
    hello(data1, data2)
    bye(hello.out)
}
```

You can specify inputs like arguments when calling the workflow:

```nextflow
workflow {
    my_workflow( channel.of('/some/data') )
}
```

The `emit:` section declares the outputs of a named workflow:

```nextflow
workflow my_workflow {
    main:
    hello(data)
    bye(hello.out)

    emit:
    bye.out
}
```

When calling the workflow, the output can be accessed using the `out` property, i.e. `my_workflow.out`.

If an output is assigned to a name, the name can be used to reference the output from the calling workflow:

```nextflow
workflow my_workflow {
    main:
    hello(data)
    bye(hello.out)

    emit:
    my_data = bye.out
}
```

You can access the result of this workflow using `my_workflow.out.my_data`.

:::{note}
Every output must be assigned to a name when multiple outputs are declared.
:::

:::{versionadded} 25.10.0
:::

When using the {ref}`strict syntax <strict-syntax-page>`, workflow takes and emits can specify a type annotation:

```nextflow
workflow my_workflow {
    take:
    data: Channel<Path>

    main:
    ch_hello = hello(data)
    ch_bye = bye(ch_hello.collect())

    emit:
    my_data: Value<Path> = ch_bye
}
```

In this example, `my_workflow` takes a channel of files (`Channel<Path>`) and emits a dataflow value with a single file (`Value<Path>`). See {ref}`stdlib-types` for the list of available types.

(dataflow-page)=

## Dataflow

Workflows consist of *dataflow* logic, in which processes are connected to each other through *dataflow channels* and *dataflow values*.

(dataflow-type-channel)=

### Channels

A *dataflow channel* (or simply *channel*) is an asynchronous sequence of values.

The values in a channel cannot be accessed directly, but only through an operator or process. For example:

```nextflow
channel.of(1, 2, 3).view { v -> "channel emits ${v}" }
```

```console
channel emits 1
channel emits 2
channel emits 3
```

**Factories**

A channel can be created by factories in the `channel` namespace. For example, the `channel.fromPath()` factory creates a channel from a file name or glob pattern, similar to the `files()` function:

```nextflow
channel.fromPath('input/*.txt').view()
```

See {ref}`channel-factory` for the full list of channel factories.

**Operators**

Channel operators, or *operators* for short, are functions that consume and produce channels. Because channels are asynchronous, operators are necessary to manipulate the values in a channel. Operators are particularly useful for implementing glue logic between processes.

Commonly used operators include:

- {ref}`operator-combine`: Emit the combinations of two channels

- {ref}`operator-collect`: Collect the values from a channel into a list

- {ref}`operator-filter`: Select the values in a channel that satisfy a condition

- {ref}`operator-flatMap`: Transform each value from a channel into a list and emit each list element separately

- {ref}`operator-grouptuple`: Group the values from a channel based on a grouping key

- {ref}`operator-join`: Join the values from two channels based on a matching key

- {ref}`operator-map`: Transform each value from a channel with a mapping function

- {ref}`operator-mix`: Emit the values from multiple channels

- {ref}`operator-view`: Print each value in a channel to standard output

See {ref}`operator-page` for the full list of operators.

(dataflow-type-value)=

### Values

A *dataflow value* is an asynchronous value created using the {ref}`channel.value <channel-value>` factory or by processes under {ref}`certain conditions <process-out-singleton>`.

You can access dataflow values directly through operators or processes:

```nextflow
channel.value(1).view { v -> "dataflow value is ${v}" }
```

```console
dataflow value is 1
```

See {ref}`stdlib-types-value` for the set of available methods for dataflow values.

(workflow-process-invocation)=

### Calling processes and workflows

Process and workflow invocation is the mechanism for executing processes and composing workflows by passing channel data between them. Processes and workflows are called like functions, passing their inputs as arguments:

```nextflow
process hello {
    output:
    path 'hello.txt', emit: txt

    script:
    """
    your_command > hello.txt
    """
}

process bye {
    input:
    path 'hello.txt'

    output:
    path 'bye.txt', emit: txt

    script:
    """
    another_command hello.txt > bye.txt
    """
}

workflow hello_bye {
    take:
    data

    main:
    hello()
    bye(data)
}

workflow {
    data = channel.fromPath('/some/path/*.txt')
    hello_bye(data)
}
```

Only workflows can call processes and other workflows. Each process or workflow can only be called once per workflow. Use {ref}`module-aliases` to call them multiple times.

**Accessing outputs**

Process and workflow outputs can be assigned to variables, passed to other calls, or accessed using the `.out` property:

```nextflow
workflow {
    data = channel.fromPath('/some/path/*.txt')

    // Assign to variable
    result = hello_bye(data)

    // Access using .out property
    hello_bye.out.bye.view()
}
```

Named outputs are accessed as properties:

```nextflow
workflow hello_bye {
    main:
    hello()
    bye(hello.out)

    emit:
    bye_result = bye.out.txt
}

workflow {
    hello_bye(data)
    hello_bye.out.bye_result.view()
}
```

:::{note}
You can also access outputs by index (e.g., `hello.out[0]`), but use named outputs when possible.
:::

Workflows can be composed in the same way:

```nextflow
workflow flow1 {
    take:
    data

    main:
    tick(data)
    tack(tick.out)

    emit:
    tack.out
}

workflow flow2 {
    take:
    data

    main:
    tick(data)
    tock(tick.out)

    emit:
    tock.out
}

workflow {
    data = channel.fromPath('/some/path/*.txt')
    flow1(data)
    flow2(flow1.out)
}
```

:::{note}
You can call the same process in different workflows without using an alias. For example, `tick` in this example is used in both `flow1` and `flow2`. The workflow call stack determines the *fully qualified process name*, which distinguishes the different process calls, i.e. `flow1:tick` and `flow2:tick` in the above example.
:::

:::{tip}
You can use the fully qualified process name as a {ref}`process selector <config-process-selectors>` in a Nextflow configuration file, and it takes priority over the simple process name.
:::

(workflow-special-operators)=

### Special operators

Special operators are workflow-specific operators (`|` and `&`) that provide shorthand syntax for chaining and parallelizing process calls. The following operators have a special meaning when used in a workflow with process and workflow calls.

:::{note}
As a best practice, avoid these operators when {ref}`type checking <preparing-static-types>` is enabled. Using these operators will prevent the type checker from validating your code.
:::

**Pipe `|`**

You can use the `|` operator to chain processes, operators, and workflows:

```nextflow
process greet {
    input:
    val data

    output:
    val result

    exec:
    result = "$data world"
}

workflow {
    channel.of('Hello', 'Hola', 'Ciao')
        | greet
        | map { v -> v.toUpperCase() }
        | view
}
```

**And `&`**

You can use the `&` operator to call multiple processes in parallel with the same channel:

```nextflow
process greet {
    input:
    val data

    output:
    val result

    exec:
    result = "$data world"
}

process to_upper {
    input:
    val data

    output:
    val result

    exec:
    result = data.toUpperCase()
}

workflow {
    channel.of('Hello')
        | map { v -> v.reverse() }
        | (greet & to_upper)
        | mix
        | view
}
```

(workflow-recursion)=

### Process and workflow recursion

:::{versionadded} 22.04.0
:::

:::{note}
This feature requires the `nextflow.preview.recursion` feature flag to be enabled.
:::

Process and workflow recursion is a pattern that allows a process or workflow to invoke itself repeatedly until a termination condition is met. You can invoke processes recursively using the `recurse` method.

```{literalinclude} snippets/recurse-process.nf
:language: nextflow
```

```{literalinclude} snippets/recurse-process.out
:language: console
```

In this example, the `count_down` process first runs with the value `params.start`. On each subsequent iteration, the process is invoked again using the output from the previous iteration. The recursion continues until the specified condition is satisfied, as defined by the `until` method, which terminates the recursion.

The recursive output can also be limited using the `times` method:

```nextflow
count_down
    .recurse(params.start)
    .times(3)
    .view { v -> "${v}..." }
```

Workflows can also be invoked recursively:

```{literalinclude} snippets/recurse-workflow.nf
:language: nextflow
```

```{literalinclude} snippets/recurse-workflow.out
:language: console
```

**Limitations**

- A recursive process or workflow must have matching inputs and outputs so that each iteration can supply its outputs as inputs for the next iteration.

- Recursive workflows cannot use *reduction* operators such as `collect`, `reduce`, and `toList`, because these operators cause the recursion to hang indefinitely after the initial iteration.
