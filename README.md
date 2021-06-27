# oink

A library and microservice for LOINC .

> *Status* : in *very early* development; at the REPL can import and build a file-backed
database and provide a rudimentary API for LOINC data.

LOINC is an international standard for health measurements, observations and 
documents published by the Regenstrief Institute.

`oink` makes it easy to use LOINC in your own software. It is composable with
other tools extending identifier resolution and mapping to LOINC code
systems. 

# Usage

`oink` is designed as a command-line utility, a microservice and a library.

As a command-line utility, it can import a LOINC distribution into a file-based
database. This takes six minutes.

As a microservice, it provides a simple RESTful web service to provide access
to LOINC data.

As a library, it provides a simple API in order to make use of LOINC data.

`oink` provides a graph-like API designed to be composed with other graph data
resolvers as well as simpler end-points.

The easiest way to run `oink` is to [download clojure](https://clojure.org/guides/getting_started)
and then clone this repository:

```shell
git clone https://github.com/wardle/oink
cd oink
```

## Command-line usage

To create a LOINC database, download the [complete download file](https://loinc.org/download/loinc-and-relma-complete-download-file/) and unzip to a 
directory.

Once downloaded, simply run
```shell
clj -M:run import --db /var/data/oink-2021-06 /path/to/download
```

This will create a file-based database at 
/var/data/oink-2021-06 based on the contents of the download. It takes
about 6 minutes on my machine to import the data.

## Use as a microservice

> *** This has not yet been implemented

Simply run 

```shell
clj -M:run serve --db /var/data/oink-2021-06
```

## Use at the REPL

I make very great use of REPL-driven development. This allows me to explore the
LOINC distribution interactively while writing code. If you look at any `comment`
block in the source code, you will see the artifacts of that development - 
in general I write code in the editor and send those forms to a running 
application; getting feedback immediately. It's a bit like writing a domain-specific
language incrementally and iteratively.

Most clojure users run the REPL from within an integrated development 
environment such as emacs, vim, IntelliJ or Visual Studio Code. You can run 
a REPL at a command line but this is much less common.

To run a REPL at the command-line, just run:

```shell
clj
```

## Use as a library

Use a github coordinate in your deps.edn file, or use the pre-built jar
file. 

# Development

`oink` is a thin wrapper around the source LOINC data. 

To help you get started, here is an introduction to the important namespaces.

### `com.eldrix.oink.importer`

Provides streaming of the LOINC CSV data using clojure's core.async library. 
Data are read, turned into batches of CSV data and sent on a channel with the 
type derived from the filename. 

### `com.eldrix.oink.core`

This is the core API namespace. It leverages `importer` to stream data and
store in a EAV backing store provided by [datalevin](https://github.com/juji-io/datalevin). 
This is implemented by an LMDB key value store, providing indices for entities, 
attributes and values. We use datalog to slice and dice the LOINC data for API 
access and to build a more optimised index for search.

### `com.eldrix.oink.index`

This will provide a Lucene-backed search index, permitting search of LOINC
entities via a range of different facets. 

### `com.eldrix.oink.graph`

This will provide a graph-based API to LOINC data, including resolution of 
LOINC part components to alternative terminologies such as SNOMED CT.

### List outdated dependencies

```shell
clj -M:outdated
```
