# oink

A library and microservice for LOINC .

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

### List outdated dependencies

```shell
clj -M:outdated
```
