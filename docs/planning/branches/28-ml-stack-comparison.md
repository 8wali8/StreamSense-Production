# ml-engine backend comparison for hardening/28

Produced by `tools/ml/compare_backends.py` (see `28-ml-stack-upgrade.md`): the real sentiment (`cardiffnlp/twitter-roberta-base-sentiment-latest`) and relevance (`sentence-transformers/all-MiniLM-L6-v2`) backends scored the 10 messages of the Twitch replay chat fixture plus 12 built-in sentences before and after the upgrade, in `python:3.11.16-slim` on CPU. Eleven relevance inputs went through the embedding model; the other eleven were answered by the direct sponsor match, which does not use the model.


| Library | Before | After |
|---|---|---|
| torch | 2.2.2+cpu | 2.14.0+cpu |
| torchvision | 0.17.2+cpu | 0.29.0+cpu |
| transformers | 4.40.2 | 5.16.1 |
| sentence-transformers | 2.6.1 | 6.0.1 |
| safetensors | 0.8.0 | 0.8.0 |
| huggingface-hub | 0.36.2 | 1.30.0 |
| tokenizers | 0.19.1 | 0.23.2 |
| numpy | 1.26.4 | 1.26.4 |

## sentiment: 22/22 labels agree, max |score delta| 0.0000, mean 0.0000

| Input | Before | After | Score before | Score after |
|---|---|---|---|---|
| worst stream in weeks, the audio keeps cutting out | NEGATIVE | NEGATIVE | -0.9500 | -0.9500 |
| what time does the next segment start | NEUTRAL | NEUTRAL | 0.0120 | 0.0120 |
| this stream is great and the chat energy is strong | POSITIVE | POSITIVE | 0.9850 | 0.9850 |
| the weather in Monaco is 24 degrees | NEUTRAL | NEUTRAL | -0.0670 | -0.0670 |
| the pacing feels solid today | POSITIVE | POSITIVE | 0.9600 | 0.9600 |

## relevance: 22/22 relevants agree, max |score delta| 0.0000, mean 0.0000

| Input | Before | After | Score before | Score after |
|---|---|---|---|---|
| worst stream in weeks, the audio keeps cutting out | False | False | 0.1380 | 0.1380 |
| what time does the next segment start | False | False | 0.2330 | 0.2330 |
| this stream is great and the chat energy is strong | False | False | 0.3410 | 0.3410 |
| the weather in Monaco is 24 degrees | False | False | 0.1300 | 0.1300 |
| the pacing feels solid today | False | False | 0.1700 | 0.1700 |

