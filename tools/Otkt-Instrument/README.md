# Otkt-Instrument

## About
An instrumentation tool that works in tandem with OtktDSL.

## Author
Daphné Larrivain <daphne.larrivain@ecole.ensicaen.fr>

## Instrumentation

In order to instrument a given app with otkt, one would need to do the following:
- decorate every functiong/method definition with @intrument
- import the decorator from the otkt.tools.intrument module.

This tool does both of these in a "smart" manner.
Just prepending every import to their files and every decorator to their function is prone to break things.

This tool instruments while respecting these two principles:
- Taking context into account before adding code.
- Not every file has to be instrumented (otherwise, it will break the app).

This process is not fool proof as it relies on conventions to avoid touching potentially app-breaking files.
Consider this as a good basis to start your custom instrumentation.

Use `--help` for more info.
