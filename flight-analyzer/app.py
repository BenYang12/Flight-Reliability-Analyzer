"""
The Flask service. Two routes, and it delegates everything to model_utils.

    POST /analyze         one operation, with the LLM paragraph
    POST /analyze-batch   many operations, deterministic only

Run it with:
    flask --app app run
"""

from flask import Flask, request

import model_utils

app = Flask(__name__)


@app.post("/analyze")
def analyze():
    return model_utils.analyze(request.get_json(), with_summary=True)


@app.post("/analyze-batch")
def analyze_batch():
    # No summary per flight, deliberately. Spring calls this to classify the 20
    # recent operations behind one page; a paragraph each would be 20 API calls
    # and 20 paragraphs nobody reads. The single narrated summary comes from
    # /analyze, and the archetype and facts here are what the list view renders.
    flights = request.get_json()["flights"]
    return {"results": [model_utils.analyze(flight) for flight in flights]}
