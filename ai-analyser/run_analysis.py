import sys
import os
import uuid
import logging

# Add the app directory to sys.path to allow imports from the app.*
sys.path.append(os.path.join(os.getcwd(), 'ai-analyser'))

# Configure basic logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

from unittest.mock import MagicMock
import app.messaging.publisher
import app.analysis.llm_evaluator

# Use the real publisher
# app.messaging.publisher.publish_rumor_computed = MagicMock()

# Mock the LLM evaluator to avoid Ollama dependency during simple testing
# You can comment this out if you have Ollama running locally
# mock_evaluation = MagicMock()
# mock_evaluation.sentiment_score = -0.5
# mock_evaluation.mentions_delay = True
# mock_evaluation.confidence = 0.8
# mock_evaluation.info_type = "rumor"
# app.analysis.llm_evaluator.evaluate_post = MagicMock(return_value=mock_evaluation)

from app.pipeline import run_pipeline_for_title

def run_test_analysis(media_title: str):
    """
    Utility function to run the full analysis pipeline using only a media title.
    Generates a random UUID to satisfy internal requirements.
    """
    test_id = uuid.uuid4()
    print(f"\n>>> Running analysis for title: '{media_title}'")
    print(f">>> Generated temporary UUID: {test_id}\n")

    # Run with publish=True to use RabbitMQ
    run_pipeline_for_title(test_id, media_title, publish=True)

    print("\n>>> Analysis run triggered successfully.")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python run_analysis.py \"Media Title\"")
        sys.exit(1)

    title = sys.argv[1]
    run_test_analysis(title)
