from functools import partial

from utils.argparse import parse_args

import json

import numpy as np
from numpy import dtype

import pandas as pd

from flaml import tune

from utils.datasets import get_dataset
from hamlet.objective import objective
from hamlet.buffer import Buffer


def main(args):
    np.random.seed(args.seed)

    X, y, _ = get_dataset(args.dataset)
    buffer = Buffer(metric=args.metric, input_path=args.input_path)
    space = buffer.get_space()
    points_to_evaluate, evaluated_rewards = buffer.get_evaluations()

    # print(
    #     pd.concat(
    #         [
    #             pd.DataFrame(points_to_evaluate),
    #             pd.DataFrame(
    #                 [reward[args.metric] for reward in evaluated_rewards],
    #                 columns=[args.metric],
    #             ),
    #         ],
    #         axis=1,
    #     )
    # )

    analysis = tune.run(
        evaluation_function=partial(objective, X, y, args.metric, args.seed),
        config=space,
        metric=args.metric,
        mode=args.mode,
        num_samples=args.batch_size + len(points_to_evaluate),
        points_to_evaluate=points_to_evaluate,
        # evaluated_rewards=evaluated_rewards,
        verbose=False,
    )

    points_to_evaluate, evaluated_rewards = buffer.get_evaluations()

    automl_output = {
        "points_to_evaluate": points_to_evaluate[-args.batch_size :],
        "evaluated_rewards": [
            str(reward[args.metric]) for reward in evaluated_rewards[-args.batch_size :]
        ],
        "rules": [],
    }

    with open(args.output_path, "w") as outfile:
        json.dump(automl_output, outfile)

    # results_df = pd.concat(
    #     [
    #         pd.DataFrame(points_to_evaluate[-args.batch_size:]),
    #         pd.DataFrame(
    #             [reward[args.metric] for reward in evaluated_rewards[-args.batch_size:]],
    #             columns=[args.metric],
    #         ),
    #     ],
    #     axis=1,
    # )
    # print(results_df)
    # results_df.to_csv("automl_output.csv")


if __name__ == "__main__":
    args = parse_args()
    main(args)