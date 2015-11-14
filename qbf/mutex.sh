#!/bin/bash
states=1
cd .. && ant qbf-automaton-generator-jar && cd qbf && java -Xms2G -Xmx4G -jar ../jars/qbf-automaton-generator.jar case-instances/mutex.sc --ltl case-instances/mutex.ltl --size $states --eventNumber 1 --eventNames r --actionNumber 2 --actionNames g0,g1 --varNumber 2 --timeout 200000 --result generated-fsm.gv --strategy COUNTEREXAMPLE --completenessType NORMAL --satSolver LINGELING --noCompletenessHeuristics
