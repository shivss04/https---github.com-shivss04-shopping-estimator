import json
import logging
import boto3
import requests
import time
import os

api_key = os.environ['API_KEY']

logger = logging.getLogger()
logger.setLevel(logging.INFO)

def event_contents(event):
    """
    Function to check the event and extract the audio file name
    """
    try:
        print(f'queryStringParameters in event_contents: {event['queryStringParameters']}')
        body = event['queryStringParameters']['filename']
        return body

    except Exception as e:
        logger.error("ERROR: Unexpected error in event_contents()")
        logger.error(e)

def api_invocation_stt(s3_file_name):
    """
    Function to pass audio file to OpenAI's Whisper API and get a transcription
    """
    
    print(f'In api_stt Invocation')
    s3_client = boto3.client('s3')

    try:
        obj = s3_client.get_object(Bucket='shopping-cost-estimator-bucket', Key=f'{s3_file_name}')
        audio_bytes = obj['Body'].read()
        print(f'audio_bytes : {audio_bytes[:10]}')
        print({type(audio_bytes)})

        temp_audio_path = f"/tmp/{s3_file_name}"
        with open(temp_audio_path, "wb") as f:
            f.write(audio_bytes)

        api_url = "https://api.openai.com/v1/audio/transcriptions"
        headers = {"Authorization": f"Bearer {api_key}"}
        files = {
            "file": (s3_file_name, open(temp_audio_path, "rb"), "audio/mp3"),
            "model": (None, "whisper-1"),
            "response_format": (None, "json"),
            "prompt": (None, "Give me a short list of what I want to buy. For example I might say I uh want to buy a womens t shirt and a mens joggers, the response should be (Womens T-Shirt, Men's Joggers)"),
        }

        response = requests.post(api_url, headers=headers, files=files)
        response.raise_for_status()
        result = response.json()
        api_response_string = result['text']        
        return api_response_string
    
    except Exception as e:
        logger.error("ERROR: Unexpected error in api_invocation_stt()")
        logger.error(e)
        return f'api_invocation did not work'

def api_invocation_list(response_string_transcription):
    """
    Function to send transcription and receive a comma separated list
    """

    print(f'In api Invocation list')

    try:
        api_url = "https://api.openai.com/v1/chat/completions"
        headers_ = {'Content-Type' : 'application/json', 'Authorization' : f'Bearer {api_key}'}
        request_data = {
            "model": "gpt-4o-mini",
            "messages": [
                {
                    "role": "system",
                    "content": "Just give me a comma separated list of what I want to buy.  Default it to women's unless specified. So if I say I want to buy a mens t shirt and joggers, your response should be Men's T-Shirt, Women's joggers."
                },
                {
                    "role": "user",
                    "content" : f"{response_string_transcription}"
                }
            ]
        }

        api_response = requests.post(f'{api_url}', data = json.dumps(request_data), headers = headers_, timeout = 45)
        response_json = api_response.json()
        api_response_string = response_json['choices'][0]['message']['content']
        return api_response_string
    
    except Exception as e:
        logger.error("ERROR: Unexpected error in api_invocation_list()")
        logger.error(e)
        return f'api_invocation did not work'


def lambda_handler(event, context):
    print(f'event: {event}')
    s3_file_name = event_contents(event)
    print(f's3_file_name = {s3_file_name}')
    response_string_transcription =  api_invocation_stt(s3_file_name)
    response_string = api_invocation_list(response_string_transcription)
    print(f'response_string in main : {response_string}')

    return {
        'statusCode': 200,
        'body': json.dumps(f'{response_string}')
    }
