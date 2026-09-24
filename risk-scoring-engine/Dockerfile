FROM python:3.12-slim
WORKDIR /app

# Install dependencies
COPY risk-scoring-engine/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source
COPY risk-scoring-engine/app ./app

# Copy contracts schemas so the schema path resolves at runtime.
# The app reads: ../../contracts/schemas/score.schema.json relative to app/main.py
# which resolves to /app/../../contracts/... → we embed it next to the app dir.
COPY contracts/schemas ./contracts/schemas

EXPOSE 8084
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8084"]
