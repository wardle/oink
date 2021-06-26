# oink

A library and microservice for LOINC .

> *Status* : in development; at the REPL can import and build a file-backed
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

## Command-line usage

## Use as a microservice

## Use as a library

# Development

`oink` is a thin wrapper around the source LOINC data. 

To help you get started, here is an introduction to the important namespaces.

### `com.eldrix.oink.importer`

Provides fast, asynchronous streaming of the LOINC CSV data using clojure's
core.async library. Data are read, turned into batches of CSV data and sent
on a channel with the type derived from the filename. 

### `com.eldrix.oink.core`

This is the core API namespace. It leverages `importer` to stream data and
store in a EAV backing store provided by [datalevin](https://github.com/juji-io/datalevin). 
This is implemented by an LMDB key value store, providing indices for entities, 
attributes and values. We use datalog to slice and dice the LOINC data for API 
access and to build a more optimised index for search.

### `com.eldrix.oink.index`

This will provide a Lucene-backed search index, permitting search of LOINC
entities via a range of different facets. 

### List outdated dependencies

```shell
clj -M:outdated
```
