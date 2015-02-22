#!/bin/bash

timeout=300
min_size=5
max_size=10

echo "Compiling..."
ant qbf-automaton-generator-jar
echo "Evaluating..."
fsm="qbf/generated-fsm.gv"

for ((size = $min_size; size <= $max_size; size++)); do
    for ((instance = 0; instance <= 49; instance++)); do
        ev_name=evaluation-daniil/$size-$instance
        if [ -f $ev_name.done ]; then
            continue
        fi
        name="qbf/testing-daniil/nstates=$size/$instance"
        sc_name=$name/plain-scenarios
        ltl_name=$name/formulae
        echo ">>> s=$size num=$instance"
        rm -f "$fsm"
        java -Xms2G -jar jars/qbf-automaton-generator.jar "$sc_name" \
            --ltl "$ltl_name" --size $size --eventNumber 2 --actionNumber 2 --varNumber 2 \
            --timeout $timeout -qs SKIZZO --result "$fsm" --strategy HYBRID \
            2>&1 | grep "\\(INFO\\|WARNING\\|SEVERE\\|Exception\\|OutOfMemoryError\\)" > $ev_name.log && touch $ev_name.done
    done
done
