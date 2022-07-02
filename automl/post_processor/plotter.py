from importlib.resources import path
import json
import os
import pandas as pd


def get_best_in(target, evaluated_rewards):
    filtered = [
        x["balanced_accuracy"] if x["balanced_accuracy"] != "-inf" else 0
        for x in evaluated_rewards[:target]
    ]
    return max(filtered)


data = {}
path = os.path.join("/", "home", "results")
for approach in ["baseline_5000", "hamlet_250", "hamlet_150"]:
    with open(os.path.join(path, approach, "summary.json")) as f:
        for dataset, result in json.load(f).items():
            if dataset not in data:
                data[dataset] = {}
            # data[dataset][approach] = result["best_config"]["balanced_accuracy"]
            data[dataset][approach] = get_best_in(1000, result["evaluated_rewards"])


pd.DataFrame.from_dict(data, orient="index").to_csv(os.path.join(path, "summary.csv"))
