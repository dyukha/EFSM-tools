#!/bin/bash

timeout=600
min_size=10
max_size=10
instances=20
events=5
actions=5

echo "Compiling..."
wd=$(pwd)
cd ../.. && ant plant-automaton-generator-jar && cd "$wd"
echo "Evaluating..."

fsm="generated-plant.gv"

mkdir -p evaluation

for ((size = $min_size; size <= $max_size; size++)); do
    for ((instance = 0; instance < $instances; instance++)); do
        ev_name=evaluation/$size-$instance
        if [ -f $ev_name/done ]; then
            echo skipping $ev_name
            continue
        fi
        rm -rf $ev_name
        mkdir -p $ev_name
        name=plants/plant-$size-$instance
        sc_name=$name.sc
        instance_description="s=$size n=$instance"
        ltl_name=$name.ltl
        for ((trysize = $size; trysize <= $size; trysize++)); do
            echo ">>> $instance_description: $trysize"
            rm -f $fsm
            java -Xms2G -Xmx4G -jar ../../jars/plant-automaton-generator.jar "$sc_name" \
                --ltl "$ltl_name" --size $trysize --eventNumber $events --actionNumber $actions \
                --timeout $timeout --result "$fsm" 2>&1 | cat > $ev_name/$trysize.full.log
            grep "\\(INFO\\|WARNING\\|SEVERE\\|Exception\\|OutOfMemoryError\\)" < $ev_name/$trysize.full.log > $ev_name/$trysize.log
            if [[ $(grep "\\(TIME LIMIT EXCEEDED\\|UNKNOWN\\|OutOfMemoryError\\)" < $ev_name/$trysize.log) != "" ]]; then
                # unknown
                echo $trysize > $ev_name/size
                touch $ev_name/unknown $ev_name/done
                break
            elif [ -f $fsm ]; then
                # sat
                echo $trysize > $ev_name/size
                touch $ev_name/found $ev_name/done
                cp $fsm $ev_name/$fsm
                break
            fi  # else continue
        done
    done
done
