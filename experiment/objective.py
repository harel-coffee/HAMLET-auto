# Scikit-learn provides a set of machine learning techniques
import traceback
import numpy as np
from sklearn.pipeline import Pipeline
from sklearn.model_selection import cross_validate
from sklearn.preprocessing import FunctionTransformer

## Feature Engineering operators
from sklearn.decomposition import PCA
from sklearn.feature_selection import SelectKBest
from sklearn.pipeline import FeatureUnion

## Normalization operators
from sklearn.preprocessing import (
    RobustScaler,
    StandardScaler,
    MinMaxScaler,
    PowerTransformer,
)

## Classification algorithms
from sklearn.neighbors import KNeighborsClassifier

from hamlet.Buffer import Buffer


def get_prototype(config):
    # We define the ml pipeline to optimize (i.e., the order of the pre-processing transformations + the ml algorithm)
    ml_pipeline = config["Prototype"]
    if ml_pipeline is None:
        raise NameError("No prototype specified")
    else:
        ml_pipeline = ml_pipeline.split("_")
    return ml_pipeline


def instantiate_pipeline(prototype, seed, config):
    # In such a precise order:
    pipeline = []
    for step in prototype:
        # we define the parametrization of each step,
        operator_parameters = {
            param_name: config[step][param_name]
            for param_name in config[step]
            if param_name != "type"
        }
        # we instantiate the operator/algorithm,
        if "random_state" in globals()[config[step]["type"]]().get_params():
            operator = globals()[config[step]["type"]](
                random_state=seed, **operator_parameters
            )
        else:
            operator = globals()[config[step]["type"]](**operator_parameters)
        # and we add it to the pipeline
        pipeline.append([step, operator])
    return Pipeline(pipeline)


# We define the function to optimize
def objective(X, y, metric, seed, config):
    result = {"accuracy": float("-inf"), "status": "fail"}
    try:
        prototype = get_prototype(config)

        pipeline = instantiate_pipeline(prototype, seed, config)

        # We evaluate the pipeline with k-cross validarion
        scores = cross_validate(
            pipeline,
            X.copy(),
            y.copy(),
            scoring=[metric],
            cv=10,
            return_estimator=False,
            return_train_score=False,
            verbose=0,
        )

        # We get the accuracy
        accuracy = np.mean(scores["test_" + metric])
        result[metric] = accuracy
        result["status"] = "success"

    except Exception as e:
        print(
            f"""MyException: {e}
              {traceback.print_exc()}"""
        )

    Buffer().add_evaluation(config=config, result=result)
    return result