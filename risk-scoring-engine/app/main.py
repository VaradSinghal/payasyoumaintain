import json
import os
from fastapi import FastAPI, HTTPException
from jsonschema import validate, ValidationError
from .models import ScoreRequest, ScoreResponse
from .scoring import generate_score

app = FastAPI(title="Risk Scoring Engine")

# Load the schema on startup
SCHEMA_PATH = os.path.join(os.path.dirname(__file__), '..', '..', 'contracts', 'schemas', 'score.schema.json')

try:
    with open(SCHEMA_PATH, 'r') as f:
        SCORE_SCHEMA = json.load(f)
except Exception as e:
    # In a real setup, we might copy this during build. For Python, reading relative to the monorepo is fine for local dev.
    SCORE_SCHEMA = None
    print(f"Warning: Could not load score schema from {SCHEMA_PATH}: {e}")

@app.post("/score/{vehicle_id}", response_model=ScoreResponse)
def score_vehicle(vehicle_id: str, request: ScoreRequest):
    """
    Computes the risk score for a vehicle based on its maintenance and usage history.
    """
    score_dict = generate_score(vehicle_id, request)
    
    # Validate against JSON schema to ensure we adhere to the contract
    if SCORE_SCHEMA:
        try:
            validate(instance=score_dict, schema=SCORE_SCHEMA)
        except ValidationError as e:
            raise HTTPException(status_code=500, detail=f"Generated score violates contract schema: {e.message}")
            
    return score_dict

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8084, reload=True)
