#!/bin/bash

tl=300

./evaluate.sh ITERATIVE_SAT $tl 3 10 true true
./evaluate.sh ITERATIVE_SAT $tl 3 10 true false
./evaluate.sh ITERATIVE_SAT $tl 3 10 false true
./evaluate.sh ITERATIVE_SAT $tl 3 10 false false
#./evaluate.sh HYBRID $tl 3 10 true true
#./evaluate.sh HYBRID $tl 3 10 true false
#./evaluate.sh HYBRID $tl 3 10 false true
#./evaluate.sh HYBRID $tl 3 10 false false
