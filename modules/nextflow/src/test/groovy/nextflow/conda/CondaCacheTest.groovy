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

package nextflow.conda

import java.nio.file.Files
import java.nio.file.Paths

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CondaCacheTest extends Specification {

    def 'should env file' () {

        given:
        def cache = new CondaCache()

        expect:
        !cache.isYamlFilePath('foo=1.0')
        cache.isYamlFilePath('env.yml')
        cache.isYamlFilePath('env.yaml')
    }

    def 'should text file' () {

        given:
        def cache = new CondaCache()

        expect:
        !cache.isTextFilePath('foo=1.0')
        !cache.isTextFilePath('env.yaml')
        !cache.isTextFilePath('foo.txt\nbar.txt')
        cache.isTextFilePath('env.txt')
        cache.isTextFilePath('foo/bar/env.txt')
    }


    def 'should create conda env prefix path for a string env' () {

        given:
        def ENV = 'bwa=1.7.2'
        def cache = Spy(CondaCache)
        def BASE = Paths.get('/conda/envs')

        when:
        def prefix = cache.condaPrefixPath(ENV)
        then:
        1 * cache.isYamlFilePath(ENV)
        1 * cache.getCacheDir() >> BASE
        prefix.toString() == '/conda/envs/env-eaeb133f4ca62c95e9c0eec7ef8d553b'
    }

    def 'should create conda env prefix path for remote uri' () {

        given:
        def ENV = 'https://foo.com/lock-file.yml'
        def cache = Spy(CondaCache)
        def BASE = Paths.get('/conda/envs')

        when:
        def prefix = cache.condaPrefixPath(ENV)
        then:
        0 * cache.isYamlFilePath(ENV)
        1 * cache.isYamlUriPath(ENV)
        1 * cache.getCacheDir() >> BASE
        prefix.toString() == '/conda/envs/env-12c863103deed9425ce8012323f948fc'
    }

    def 'should create conda env prefix path for a yaml env file' () {

        given:
        def folder = Files.createTempDirectory('test')
        def cache = Spy(CondaCache)
        def BASE = Paths.get('/conda/envs')
        def ENV = folder.resolve('foo.yml')
        ENV.text = '''
            channels:
              - conda-forge
              - bioconda
            dependencies:
              # Default bismark
              - star=2.5.4a
              - bwa=0.7.15
            '''
            .stripIndent(true)  // https://issues.apache.org/jira/browse/GROOVY-9423
        when:
        def prefix = cache.condaPrefixPath(ENV.toString())
        then:
        1 * cache.isYamlFilePath(ENV.toString())
        1 * cache.getCacheDir() >> BASE
        prefix.toString() == "/conda/envs/env-64874f9dc9e7be788384bccef357a4f4"

        cleanup:
        folder?.deleteDir()

    }

    def 'should create conda env prefix path for a env yaml file with name' () {

        given:
        def cache = Spy(CondaCache)
        def BASE = Paths.get('/conda/envs')
        def ENV = Files.createTempFile('test','.yml')
        ENV.text = '''
            name: my-env-1.1
            channels:
              - conda-forge
              - bioconda
            dependencies:
              # Default bismark
              - star=2.5.4a
              - bwa=0.7.15
            '''
                .stripIndent(true)

        when:
        def prefix = cache.condaPrefixPath(ENV.toString())
        then:
        1 * cache.isYamlFilePath(ENV.toString())
        1 * cache.getCacheDir() >> BASE
        prefix.toString() == "/conda/envs/env-5b5c72e839d0c7dcabb5d06607c205fc"

    }

    def 'should create conda env prefix path for a text env file' () {

        given:
        def folder = Files.createTempDirectory('test')
        def cache = Spy(CondaCache)
        def BASE = Paths.get('/conda/envs')
        def ENV = folder.resolve('bar.txt')
        ENV.text = '''
                star=2.5.4a
                bwa=0.7.15
                multiqc=1.2.3
                '''
                .stripIndent(true)  // https://issues.apache.org/jira/browse/GROOVY-9423

        when:
        def prefix = cache.condaPrefixPath(ENV.toString())
        then:
        1 * cache.isYamlFilePath(ENV.toString())
        1 * cache.isTextFilePath(ENV.toString())
        1 * cache.getCacheDir() >> BASE
        prefix.toString() == "/conda/envs/env-85371202d8820331ff19ae89c0595497"

        cleanup:
        folder?.deleteDir()

    }

    def 'should return a conda prefix directory' () {

        given:
        def cache = Spy(CondaCache)
        def folder = Files.createTempDirectory('test')
        def ENV = folder.toString()

        when:
        def prefix = cache.condaPrefixPath(ENV)
        then:
        1 * cache.isYamlFilePath(ENV)
        0 * cache.getCacheDir()
        prefix.toString() == folder.toString()

        cleanup:
        folder?.deleteDir()
    }

    def 'should create a conda environment' () {
        given:
        def ENV = 'bwa=1.1.1'
        def PREFIX = Files.createTempDirectory('foo')
        def cache = Spy(CondaCache)

        when:
        // the prefix directory exists ==> no conda command is executed
        def result = cache.createLocalCondaEnv(ENV, PREFIX)
        then:
        0 * cache.isYamlFilePath(ENV)
        0 * cache.runCommand(_)
        result == PREFIX

        when:
        PREFIX.deleteDir()
        result = cache.createLocalCondaEnv0(ENV,PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        0 * cache.makeAbsolute(_)
        1 * cache.runCommand( "conda create --yes --quiet --prefix $PREFIX $ENV" ) >> null
        result == PREFIX
    }

    def 'should create a conda environment - using mamba' () {
        given:
        def ENV = 'bwa=1.1.1'
        def PREFIX = Files.createTempDirectory('foo')
        def cache = Spy(new CondaCache(useMamba: true))

        when:
        // the prefix directory exists ==> no mamba command is executed
        def result = cache.createLocalCondaEnv(ENV, PREFIX)
        then:
        0 * cache.isYamlFilePath(ENV)
        0 * cache.runCommand(_)
        result == PREFIX

        when:
        PREFIX.deleteDir()
        result = cache.createLocalCondaEnv0(ENV, PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        0 * cache.makeAbsolute(_)
        1 * cache.runCommand("mamba create --yes --quiet --prefix $PREFIX $ENV") >> null
        result == PREFIX
    }

    def 'should create a conda environment - using micromamba' () {
        given:
        def ENV = 'bwa=1.1.1'
        def PREFIX = Files.createTempDirectory('foo')
        def cache = Spy(new CondaCache(useMicromamba: true))

        when:
        // the prefix directory exists ==> no mamba command is executed
        def result = cache.createLocalCondaEnv(ENV, PREFIX)
        then:
        0 * cache.isYamlFilePath(ENV)
        0 * cache.runCommand(_)
        result == PREFIX

        when:
        PREFIX.deleteDir()
        result = cache.createLocalCondaEnv0(ENV, PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        0 * cache.makeAbsolute(_)
        1 * cache.runCommand("micromamba create --yes --quiet --prefix $PREFIX $ENV") >> null
        result == PREFIX
    }

    def 'should create a conda environment using mamba and remote lock file' () {
        given:
        def ENV = 'http://foo.com/some/file-lock.yml'
        def PREFIX = Files.createTempDirectory('foo')
        def cache = Spy(new CondaCache(useMamba: true))

        when:
        // the prefix directory exists ==> no mamba command is executed
        def result = cache.createLocalCondaEnv(ENV, PREFIX)
        then:
        0 * cache.isYamlFilePath(ENV)
        0 * cache.runCommand(_)
        result == PREFIX

        when:
        PREFIX.deleteDir()
        result = cache.createLocalCondaEnv0(ENV, PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        0 * cache.makeAbsolute(_)
        1 * cache.runCommand("mamba env create --yes --prefix $PREFIX --file $ENV") >> null
        result == PREFIX
    }

    def 'should create a conda environment using micromamba and remote lock file' () {
        given:
        def ENV = 'http://foo.com/some/file-lock.yml'
        def PREFIX = Files.createTempDirectory('foo')
        def cache = Spy(new CondaCache(useMicromamba: true))

        when:
        // the prefix directory exists ==> no mamba command is executed
        def result = cache.createLocalCondaEnv(ENV, PREFIX)
        then:
        0 * cache.isYamlFilePath(ENV)
        0 * cache.runCommand(_)
        result == PREFIX

        when:
        PREFIX.deleteDir()
        result = cache.createLocalCondaEnv0(ENV, PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        0 * cache.makeAbsolute(_)
        1 * cache.runCommand("micromamba env create --yes --prefix $PREFIX --file $ENV") >> null
        result == PREFIX
    }

    def 'should create conda env with options' () {
        given:
        def ENV = 'bwa=1.1.1'
        def PREFIX = Paths.get('/foo/bar')
        and:
        def cache = Spy(new CondaCache(createOptions: '--this --that'))

        when:
        def result = cache.createLocalCondaEnv0(ENV,PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        1 * cache.isTextFilePath(ENV)
        0 * cache.makeAbsolute(_)
        1 * cache.runCommand( "conda create --this --that --yes --quiet --prefix $PREFIX $ENV" ) >> null
        result == PREFIX
    }

    def 'should create conda env with options - using mamba' () {
        given:
        def ENV = 'bwa=1.1.1'
        def PREFIX = Paths.get('/foo/bar')
        and:
        def cache = Spy(new CondaCache(useMamba: true, createOptions: '--this --that'))

        when:
        def result = cache.createLocalCondaEnv0(ENV, PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        1 * cache.isTextFilePath(ENV)
        0 * cache.makeAbsolute(_)
        1 * cache.runCommand("mamba create --this --that --yes --quiet --prefix $PREFIX $ENV") >> null
        result == PREFIX
    }

    def 'should create conda env with options - using micromamba' () {
        given:
        def ENV = 'bwa=1.1.1'
        def PREFIX = Paths.get('/foo/bar')
        and:
        def cache = Spy(new CondaCache(useMicromamba: true, createOptions: '--this --that'))

        when:
        def result = cache.createLocalCondaEnv0(ENV, PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        1 * cache.isTextFilePath(ENV)
        0 * cache.makeAbsolute(_)
        1 * cache.runCommand("micromamba create --this --that --yes --quiet --prefix $PREFIX $ENV") >> null
        result == PREFIX
    }

    def 'should create conda env with channels' () {
        given:
        def ENV = 'bwa=1.1.1'
        def PREFIX = Paths.get('/foo/bar')
        and:
        def cache = Spy(new CondaCache(new CondaConfig([channels:['bioconda','defaults']])))

        when:
        def result = cache.createLocalCondaEnv0(ENV, PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        1 * cache.isTextFilePath(ENV)
        0 * cache.makeAbsolute(_)
        1 * cache.runCommand("conda create --yes --quiet --prefix /foo/bar -c bioconda -c defaults bwa=1.1.1") >> null
        result == PREFIX
    }

    def 'should create a conda env with a yaml file' () {

        given:
        def ENV = 'foo.yml'
        def PREFIX = Paths.get('/conda/envs/my-env')
        def cache = Spy(CondaCache)

        when:
        def result = cache.createLocalCondaEnv0(ENV, PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        0 * cache.isTextFilePath(ENV)
        1 * cache.makeAbsolute(ENV) >> Paths.get('/usr/base').resolve(ENV)
        1 * cache.runCommand( "conda env create --prefix $PREFIX --file /usr/base/foo.yml" ) >> null
        result == PREFIX

    }

    def 'should create a conda env with a yaml file - using micromamba' () {

        given:
        def ENV = 'foo.yml'
        def PREFIX = Paths.get('/conda/envs/my-env')
        def cache = Spy(new CondaCache(useMicromamba: true))

        when:
        def result = cache.createLocalCondaEnv0(ENV, PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        0 * cache.isTextFilePath(ENV)
        1 * cache.makeAbsolute(ENV) >> Paths.get('/usr/base').resolve(ENV)
        1 * cache.runCommand( "micromamba env create --yes --prefix $PREFIX --file /usr/base/foo.yml" ) >> null
        result == PREFIX

    }

    def 'should create a conda env with a text file' () {

        given:
        def ENV = 'foo.txt'
        def PREFIX = Paths.get('/conda/envs/my-env')
        and:
        def cache = Spy(new CondaCache(createOptions: '--this --that'))

        when:
        def result = cache.createLocalCondaEnv0(ENV, PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        1 * cache.isTextFilePath(ENV)
        1 * cache.makeAbsolute(ENV) >> Paths.get('/usr/base').resolve(ENV)
        1 * cache.runCommand( "conda create --this --that --yes --quiet --prefix $PREFIX --file /usr/base/foo.txt" ) >> null
        result == PREFIX

    }

    def 'should create a conda env with a text file - using micromamba' () {

        given:
        def ENV = 'foo.txt'
        def PREFIX = Paths.get('/conda/envs/my-env')
        and:
        def cache = Spy(new CondaCache(useMicromamba: true, createOptions: '--this --that'))

        when:
        def result = cache.createLocalCondaEnv0(ENV, PREFIX)
        then:
        1 * cache.isYamlFilePath(ENV)
        1 * cache.isTextFilePath(ENV)
        1 * cache.makeAbsolute(ENV) >> Paths.get('/usr/base').resolve(ENV)
        1 * cache.runCommand( "micromamba create --this --that --yes --quiet --prefix $PREFIX --file /usr/base/foo.txt" ) >> null
        result == PREFIX

    }

    def 'should get options from the config' () {

        when:
        def cache = new CondaCache(new CondaConfig())
        then:
        cache.createTimeout.minutes == 20
        cache.createOptions == null
        cache.configCacheDir0 == null
        !cache.@useMamba
        !cache.@useMicromamba
        cache.binaryName == "conda"

        when:
        cache = new CondaCache(new CondaConfig(createTimeout: '5 min', createOptions: '--foo --bar', cacheDir: '/conda/cache', useMamba: true))
        then:
        cache.createTimeout.minutes == 5
        cache.createOptions == '--foo --bar'
        cache.configCacheDir0 == Paths.get('/conda/cache')
        cache.@useMamba
        !cache.@useMicromamba
        cache.binaryName == "mamba"

        when:
        cache = new CondaCache(new CondaConfig(createTimeout: '5 min', createOptions: '--foo --bar', cacheDir: '/conda/cache', useMicromamba: true))
        then:
        cache.createTimeout.minutes == 5
        cache.createOptions == '--foo --bar'
        cache.configCacheDir0 == Paths.get('/conda/cache')
        !cache.@useMamba
        cache.@useMicromamba
        cache.binaryName == "micromamba"
    }

    def 'should define cache dir from config' () {

        given:
        def folder = Files.createTempDirectory('test'); folder.deleteDir()
        def config = new CondaConfig(cacheDir: folder.toString())
        CondaCache cache = Spy(CondaCache, constructorArgs: [config])

        when:
        def result = cache.getCacheDir()
        then:
        0 * cache.getSessionWorkDir()
        result == folder
        result.exists()

        cleanup:
        folder?.deleteDir()
    }

    def 'should define cache dir from rel path' () {

        given:
        def folder = Paths.get('.test-conda-cache-' + Math.random())
        def config = new CondaConfig(cacheDir: folder.toString())
        CondaCache cache = Spy(CondaCache, constructorArgs: [config])

        when:
        def result = cache.getCacheDir()
        println result
        then:
        0 * cache.getSessionWorkDir()
        result == folder.toAbsolutePath()
        result.exists()

        cleanup:
        folder?.deleteDir()
    }

    def 'should define cache dir from env' () {

        given:
        def folder = Files.createTempDirectory('test'); folder.deleteDir()
        def config = new CondaConfig()
        CondaCache cache = Spy(CondaCache, constructorArgs: [config])

        when:
        def result = cache.getCacheDir()
        then:
        2 * cache.getEnv() >> [NXF_CONDA_CACHEDIR: folder.toString()]
        0 * cache.getSessionWorkDir()
        result == folder
        result.exists()

        cleanup:
        folder?.deleteDir()
    }

    def 'should define cache dir from session workdir' () {

        given:
        def folder = Files.createTempDirectory('test');
        def cache = Spy(CondaCache)

        when:
        def result = cache.getCacheDir()
        then:
        1 * cache.getSessionWorkDir() >> folder
        result == folder.resolve('conda')
        result.exists()

        cleanup:
        folder?.deleteDir()
    }
}
