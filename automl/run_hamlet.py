import subprocess
import openml
import os
import argparse

from multiprocessing import Pool
from tqdm import tqdm


def get_input(iteration, dataset_path):
    if iteration == 0:
        return "$(pwd)/resources/complete_kb_5_steps.txt", lambda: None

    input = f"{dataset_path}/argumentation/complete_kb_{iteration}.txt"

    def read_content(path):
        with open(path, "r") as file:
            return file.read()

    def execute():
        kb = read_content(f"{dataset_path}/argumentation/kb_{iteration}.txt")
        rules = read_content(f"{dataset_path}/argumentation/rules_{iteration}.txt")
        with open(input, "w+") as file:
            file.write(kb + "\n" + rules + "\n")

    return (input, lambda: execute())


def get_commands(data, args):
    commands = []
    for dataset in data:
        for iteration in range(0, args.iterations):
            dataset_path = os.path.join(os.getcwd(), args.workspace, str(dataset))
            log_path = create_directory(dataset_path, "logs")
            input_path, before_execute = get_input(iteration, dataset_path)
            cmd = f"""java -jar hamlet-{args.version}-all.jar \
                        {dataset_path} \
                        {dataset} \
                        {args.metric} \
                        {args.mode} \
                        {args.batch_size} \
                        42 \
                        false \
                        {input_path}"""
            stdout_path = os.path.join(log_path, f"stdout_{iteration + 1}.txt")
            stderr_path = os.path.join(log_path, f"stderr_{iteration + 1}.txt")
            commands.append((cmd, stdout_path, stderr_path, before_execute))
    return commands


def run_cmd(cmd, stdout_path, stderr_path):
    open(stdout_path, "w")
    open(stderr_path, "w")
    with open(stdout_path, "a") as log_out:
        with open(stderr_path, "a") as log_err:
            subprocess.call(cmd, stdout=log_out, stderr=log_err, bufsize=0, shell=True)


def create_directory(result_path, directory):
    result_path = os.path.join(result_path, directory)

    if not os.path.exists(result_path):
        os.makedirs(result_path)

    return result_path


def parse_args():
    parser = argparse.ArgumentParser(
        description="Automated Machine Learning Workflow creation and configuration"
    )
    parser.add_argument(
        "-workspace",
        "--workspace",
        nargs="?",
        type=str,
        required=False,
        help="where to save the data",
    )
    parser.add_argument(
        "-metric",
        "--metric",
        nargs="?",
        type=str,
        required=False,
        help="metric to optimize",
    )
    parser.add_argument(
        "-mode",
        "--mode",
        nargs="?",
        type=str,
        required=False,
        help="how to optimize the metric",
    )
    parser.add_argument(
        "-batch_size",
        "--batch_size",
        nargs="?",
        type=str,
        required=False,
        help="automl confs to visit",
    )
    parser.add_argument(
        "-version",
        "--version",
        nargs="?",
        type=str,
        required=False,
        help="hamlet version to run",
    )
    parser.add_argument(
        "-range",
        "--range",
        nargs="?",
        type=int,
        required=False,
        help="which index of the suite to start",
    )
    parser.add_argument(
        "-num_tasks",
        "--num_tasks",
        nargs="?",
        type=int,
        required=False,
        help="which index of the suite to start",
    )
    parser.add_argument(
        "-iterations",
        "--iterations",
        nargs="?",
        type=int,
        required=True,
        help="number of opimization iterations to perform",
    )
    args = parser.parse_args()
    return args


args = parse_args()
data = openml.study.get_suite("OpenML-CC18").data
data = data[args.range : args.range + int(len(data) / args.num_tasks)]
commands = get_commands(data, args)


with tqdm(total=len(data) * args.iterations) as pbar:
    for cmd, stdout_path, stderr_path, before_execute in get_commands(data, args):
        before_execute()
        print(cmd)
        run_cmd(cmd, stdout_path, stderr_path)
        pbar.update()
