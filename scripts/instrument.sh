#/usr/bin/env bash

# * Prepend the following line to all Python files of the target program.
#
#   from otkt.instrument import instrument
#

for py in `find . -not -path '*/[@.]*' -type f -name "*py"` ; do sed -i '1 i\from otkt.instrument import instrument' ${py} ; done

# * Prepend the following line before all Python method definitions:
#
#   @instrument
#
for py in `find . -not -path '*/[@.]*' -type f -name "*py"` ; do sed -i 's/^\([\ \t]*\)\(def \)\(.*\)$/\1@instrument\n\1\2\3/g' ${py} ; done

