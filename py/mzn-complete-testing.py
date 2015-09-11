# Author: Vladimir Ulyantsev
# Script for testing EFSM builder
# Dissertation research

import datetime
import os
import sys
import re
import subprocess
import json

COMMAND_TEMPLATE = 'java -Xmx512M -Xss128M -jar %s %s'

MZN_TEMPLATE = 'mzn-cpx.bat exact_complete_EFSM_model.mzn -d %s -o %s --soln-sep ""'


RESULTS_ROOT = 'results'

AG_PATH = 'jars/automaton-generator.jar'
AG_PARAMS_TEMPLATE = '-s %(size)s -ac %(ac)d -ec %(ec)d -vc %(vc)d -o %(path)s -mina %(mina)d -maxa %(maxa)d -p %(persent)d'

SG_PATH = 'jars/scenarios-generator.jar'
SG_PARAMS_TEMPLATE = '-a %(automaton_path)s -cnt %(count)d -o %(path)s -minl %(minl)d -maxl %(maxl)d'
SG_PARAMS_TEMPLATE_COVER = '-a %(automaton_path)s -o %(path)s -suml %(suml)d -c'

MG_PATH = 'jars/minizinc-generator.jar'
MG_PARAMS_TEMPLATE = '%(scenarios_path)s -s %(size)s -o %(output)s'

BT_PATH = 'jars/qbf-automaton-generator.jar'
BT_PARAMS_TEMPLATE = '%(path)s --size %(size)d --eventNumber %(ecnt)d --eventNames %(events)s --actionNumber %(acnt)d --actionNames %(actions)s --varNumber %(vcnt)d --timeout %(timeout)d --result %(output)s --strategy BACKTRACKING --ensureCoverageAndWeakCompleteness'

TR_PERSENT, EVENTS_COUNT, ACTIONS_COUNT, VARS_COUNT, MIN_ACTIONS, MAX_ACTIONS = 50, 4, 2, 1, 1, 2
MIN_LEN_C, MAX_LEN_C = 1, 3

LAUNCHES_COUNT = 10
SIZES = [5, 10]

SUM_LEN_MULT = [50]

TIMEOUT = 3600

def generate_automaton(automaton_path, size):
    ag_params = {'size' : size,
                 'ac' : ACTIONS_COUNT,
                 'ec' : EVENTS_COUNT,
                 'vc' : VARS_COUNT, 
                 'path' : automaton_path,
                 'mina' : MIN_ACTIONS,
                 'maxa' : MAX_ACTIONS,
                 'persent' : TR_PERSENT}
    ag_params_str = AG_PARAMS_TEMPLATE % ag_params
    ag_command = COMMAND_TEMPLATE % (AG_PATH, ag_params_str)
    os.system(ag_command)

def generate_scenarios(scenarios_path, automaton_path, size, sum_length):
    sg_params = {'automaton_path' : automaton_path,
                 'path' : scenarios_path,
                 'suml' : sum_length}
    sg_params_str = SG_PARAMS_TEMPLATE_COVER % sg_params
    sg_command = COMMAND_TEMPLATE % (SG_PATH, sg_params_str)

    os.system(sg_command)

def generate_mzn(scenarios_path, size, output_path):
    gm_params = {'scenarios_path' : scenarios_path,
                 'size' : size,
                 'output' : output_path}
    gm_params_str = SG_PARAMS_TEMPLATE_COVER % gm_params
    gm_command = COMMAND_TEMPLATE % (MG_PATH, gm_params_str)

    os.system(gm_command)


def build_automaton(command, result_path, scenarios_path):
    
    builder_command = command % (scenarios_path, result_path)
    # start time
    start_time = datetime.datetime.now()
    os.system(builder_command)
    # end time
    end_time = datetime.datetime.now() 
    
    os.system('python postprocess.py ' + result_path)

    return end_time - start_time

def launch(dir_path, builder_path, size, sum_length):
    automaton_path = os.path.join(dir_path, 'automaton.gv')
    generate_automaton(automaton_path, size)

    scenarios_path = os.path.join(dir_path, 'scenarios')
    sc_count = generate_scenarios(scenarios_path, automaton_path, size, sum_length)
    
    result_path = os.path.join(dir_path, 'result.gv')
    was_found, tree_size, function_ex_time = build_automaton(builder_path, size, dir_path, result_path, scenarios_path)

    compl_path = os.path.join(dir_path, 'completeness-result')
    is_complete = check_completeness(compl_path, result_path)

    result = {'size' : size,
              'sc_count' : sc_count,
              'sum_length' : sum_length,
              'tree_size' : tree_size,
              'function_ex_time' : function_ex_time,
              'result' : was_found,
              'time' : function_ex_time,
              'is_complete' : is_complete,
              'is_isomorph' : is_isomorph,
              'forward_cc_persent' : forward_cc_persent,
              'backward_cc_persent': backward_cc_persent}
    
    return result

def main():
    if not os.path.exists(RESULTS_ROOT):
        os.mkdir(RESULTS_ROOT)

    dt = datetime.datetime.now()
    time_str = dt.strftime('%Y-%m-%d--%H-%M-%S')
    results_dir = os.path.join(RESULTS_ROOT, time_str)
    os.mkdir(results_dir)

    print >>open(results_dir + '/experiment-properties', 'w'), get_properties_str(builder_path)
    
    results = []
    for automaton_size in SIZES:
        cur_size_dir = "%s/%02d-states" % (results_dir, automaton_size)
        os.mkdir(cur_size_dir)
        for sum_length in automaton_size * SUM_LEN_MULT:
            cur_length_dir = "%s/%04d-sum" % (cur_size_dir, sum_length)
            os.mkdir(cur_length_dir)
            for i in xrange(LAUNCHES_COUNT):
                launch_dir = "%s/%02d" % (cur_length_dir, i)
                os.mkdir(launch_dir)
                res = launch(launch_dir, builder_path, automaton_size, sum_length)
                results.append(res)
                print res
    
    results_json_str = '[' + ',\n'.join([json.dumps(r) for r in results]) + ']'
    print >>open(results_dir + '/results.json', 'w'), results_json_str

if __name__ == '__main__':
    if len(sys.argv) != 1:
        print 'Usage: %s'
    else:
        main()
