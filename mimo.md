# 语音识别（MiMo-V2.5-ASR）
## 支持的音频格式
目前仅支持 wav 和 mp3 格式的音频样本文件，传入前需将音频文件转换为 Base64 编码字符串，Base64 编码后的字符串大小上限为 10 MB。当前支持两种音频传入方式：
```Data URL
"input_audio": {
    "data": "data:{MIME_TYPE};base64,$BASE64_AUDIO"
}
```

```Base64
以纯 Base64 编码的方式传入音频，需要同时传入 format 字段指定音频格式。
"input_audio": {
    "data": "$BASE64_AUDIO",
    "format": "{format}"
}
```

支持的格式及对应 MIME 类型：

格式	MIME 类型
wav	audio/wav
mp3	audio/mpeg 或 audio/mp3

### 调用示例
- 非流式调用
```python sdk
import os
import base64
from openai import OpenAI

client = OpenAI(
    api_key=os.environ.get("MIMO_API_KEY"),
    base_url="https://api.xiaomimimo.com/v1"
)

# 需替换为本地真实的文件路径
with open("audio_file.wav", "rb") as f:
    audio_bytes = f.read()
audio_base64 = base64.b64encode(audio_bytes).decode("utf-8")

completion = client.chat.completions.create(
    model="mimo-v2.5-asr",
    messages=[
        {
            "role": "user",
            "content": [
                {
                    "type": "input_audio",
                    "input_audio": {
                        "data": f"data:audio/wav;base64,{audio_base64}"
                    }
                }
            ]
        }
    ],
    extra_body={
        "asr_options": {
            "language": "zh"
        }
    }
)

print(completion.model_dump_json())
```

- 流式调用
```python sdk
import os
import base64
from openai import OpenAI

client = OpenAI(
    api_key=os.environ.get("MIMO_API_KEY"),
    base_url="https://api.xiaomimimo.com/v1"
)

# 需替换为本地真实的文件路径
with open("audio_file.wav", "rb") as f:
    audio_bytes = f.read()
audio_base64 = base64.b64encode(audio_bytes).decode("utf-8")

completion = client.chat.completions.create(
    model="mimo-v2.5-asr",
    messages=[
        {
            "role": "user",
            "content": [
                {
                    "type": "input_audio",
                    "input_audio": {
                        "data": f"data:audio/wav;base64,{audio_base64}"
                    }
                }
            ]
        }
    ],
    extra_body={
        "asr_options": {
            "language": "auto"
        }
    },
    stream=True
)

for chunk in completion:
    print(chunk.model_dump_json())
```




# 语音合成（MiMo-V2.5-TTS 系列）


支持的模型列表
当前支持 MiMo-V2.5-TTS 系列的三种模型，模型列表如下：

Model ID	功能	音色	注意事项
mimo-v2.5-tts	使用预置精品音色进行语音合成	使用预置音色列表中的精品音色	支持唱歌模式，不支持音色设计与音色复刻
mimo-v2.5-tts-voicedesign	通过文本描述定制音色	通过文本描述自动生成音色，无需预置或音频样本	不支持唱歌模式、预置音色与音色复刻
mimo-v2.5-tts-voiceclone	基于音频样本复刻任意音色	通过音频样本精准复刻音色，实现任意声音的语音合成	不支持唱歌模式、预置音色与音色设计



## 通用注意事项
调用规则
- 语音合成的目标文本需填写在 role 为 assistant 的消息中，不可放在 user 角色的消息内。
- user 角色的消息为可选参数，可以传入指令来调整语音合成的语气与风格，也可以是对话历史（消息内容不会出现在合成的语音中）。使用 mimo-v2.5-tts-voicedesign 模型时，为必填参数。
- 采用流式调用时，输出音频的格式请指定为 pcm16，以便拼接成完整音频。拼接示例可参考各章节的 Python 调用方式。




## 风格控制
### 模型的指令遵循能力足以 cover 以下这些复杂控制（一条自然语言指令即可生效）：
- 多风格切换：同一角色在同一段语音内完成 播报 → 低语 → 嘶吼 的风格转场，过渡自然不突兀。

- 多情绪混合：支持"压抑的愤怒"、"带着哽咽的笑意"、"温柔但疲惫"、"狂躁中的温柔"等复合情绪，而非只能选单一情绪。
- 多粒度控制：从段落级（整体基调）→ 句子级（节奏）→ 词级（重音）→ 字粒度（某一个字的哽咽、拖音、气音），都可在指令中指定。

我们目前提供两种控制方法：自然语言控制 和 标签控制。两种方式的内容在 messages 中的放置位置不同：

- 自然语言控制 → 放在 role: user 的 content 中
- 音频标签控制 → 放在 role: assistant 的 content 中


自然语言控制
通过自然语言描述，让模型理解并生成对应风格的语音。内容放在 messages 中 role: user 的 content 字段。 可以直接用一句话描述想要的语音风格。

示例：
```
用轻快上扬的语调向领导报喜，语速稍快，带着查到成绩后压抑不住的激动与小骄傲，声音明亮有活力。

看着刚解决的难题成果忍不住得意忘形地惊呼，声音高亢明亮，语速偏快，语气中带着满满的自信与难以置信。

用明亮活泼的青少年嗓音，带着恶作剧得逞后的得意与戏谑，语速偏快且咬字轻巧，在强调赌注时语气微微上扬。
```

在此基础上，我们还支持一种更复杂、更精细的导演模式——像给演员写剧本一样，从角色、场景、指导三个维度全方位刻画人物与声线，模型能据此生成更富层次、更具演绎感的语音。

- 【角色】 写清人物的身份、性格底色、外形气质与说话习惯。
- 【场景】 交代此刻发生了什么、和谁说话、情绪处在什么位置。越具体越好——时间、地点、事件、对方反应都可以写进来。
- 【指导】 像导演给演员下达演绎要领：语速、气息、停顿、重音、共鸣位置、音色质感、情绪起伏。可以写得细腻，模型会按这些"舞台提示"来演。

示例：
```
角色：百年门阀岑家的现任大当家。自出生便被过继给祖庙的守门老人抚养，被塑造成一尊完美无瑕、绝情断欲的家族图腾。常年深居简出，对人有着极强的阶级疏离感。

场景：在祠堂的阴影里，看着那个不顾一切冲破保安防线来找她、企图带她私奔的男人。她要用最冷硬的阶级壁垒，绞杀对方，也绞杀自己刚刚萌芽、却足以燎原的感情。

指导：
冰冷、慵懒却极具威压的低音御姐。发声通道非常松弛，没有任何剑拔弩张，却有着让人骨里生寒的压迫感。

- 语速与顿挫：极慢，每个字都像是在舌尖滚过才吐出来，带着上位者漫不经心的傲慢。句与句之间留下极长的、令人不安的空白。
- 气声与实声：大部分时间，她的声音没有明显的声调起伏，实音重且硬，像是一条平缓却冰冷的暗河。但一定要在某些尾音处（如“真心”），加入极其轻微的气音收束，透出一丝连她自己都没察觉到的疲惫与渴望。
- 咬字肌理：文白杂糅的用词带着旧时代的痕迹，唇齿音发得极轻但极清晰（如“冲撞”“廉价”），显得既清雅又锋利，刀刀见血。
```



### 预置音色列表
使用时，可在 {"audio": {"voice": "mimo_default"}} 中设置预置音色。

音色名	|Voice ID|	语言|	性别|
MiMo-默认|	mimo_default|	因部署集群而异，中国集群默认为 冰糖，其他集群默认为 Mia|
冰糖|	冰糖|	中文|	女性|
茉莉|	茉莉|	中文|	女性|
苏打|	苏打|	中文|	男性|
白桦|	白桦|	中文|	男性|
Mia	Mia|	英文|	女性|
Chloe|	Chloe|	英文|	女性|
Milo|	Milo|	英文|	男性|
Dean|	Dean|	英文|	男性|

### 调用示例
非流式调用
```python sdk
import os
from openai import OpenAI
import base64

client = OpenAI(
    api_key=os.environ.get("MIMO_API_KEY"),
    base_url="https://api.xiaomimimo.com/v1"
)

completion = client.chat.completions.create(
    model="mimo-v2.5-tts",
    messages=[
        {
            "role": "user",
            "content": "Bright, bouncy, slightly sing-song tone — like you're bursting with good news you can barely hold in. Fast pace, rising pitch at the end."
        },
        {
            "role": "assistant",
            "content": "Hey boss — guess what, guess what? I just got the results back and I actually passed! Not just passed, I got a distinction! I know, I know — you told me I was cutting it close, but hey, here we are. Drinks are on me tonight, okay?"
        }
    ],
    audio={
        "format": "wav",
        "voice": "Chloe"
    }
)

message = completion.choices[0].message
audio_bytes = base64.b64decode(message.audio.data)
with open("audio_file.wav", "wb") as f:
    f.write(audio_bytes)
```



流式调用
```python sdk
import base64
import os
import numpy as np
import soundfile as sf
from openai import OpenAI

client = OpenAI(
    api_key=os.environ.get("MIMO_API_KEY"),
    base_url="https://api.xiaomimimo.com/v1"
)

completion = client.chat.completions.create(
    model="mimo-v2.5-tts",
    messages=[
        {
            "role": "user",
            "content": "Bright, bouncy, slightly sing-song tone — like you're bursting with good news you can barely hold in. Fast pace, rising pitch at the end."
        },
        {
            "role": "assistant",
            "content": "Hey boss — guess what, guess what? I just got the results back and I actually passed! Not just passed, I got a distinction! I know, I know — you told me I was cutting it close, but hey, here we are. Drinks are on me tonight, okay?"
        }
    ],
    audio={
        "format": "pcm16",
        "voice": "Chloe"
    },
    stream=True
)

# 24kHz PCM16LE mono audio
collected_chunks: np.ndarray = np.array([], dtype=np.float32)

for chunk in completion:
    if not chunk.choices:
        continue
    delta = chunk.choices[0].delta
    audio = getattr(delta, "audio", None)

    if audio is not None:
        assert isinstance(audio, dict), f"Expected audio to be a dict, got {type(audio)}"
        pcm_bytes = base64.b64decode(audio["data"])
        np_pcm = np.frombuffer(pcm_bytes, dtype=np.int16).astype(np.float32) / 32768.0
        collected_chunks = np.concatenate((collected_chunks, np_pcm))
        print(f"Received audio chunk of size {len(pcm_bytes)} bytes")

# Save the collected audio to a file
os.makedirs("tmp", exist_ok=True)
sf.write("tmp/output.wav", collected_chunks, samplerate=24000)
print("Audio saved to tmp/output.wav")
```


### 使用文本设计音色进行语音合成

无需提供音频文件，只需在角色为 user 的消息中添加音色描述文本，即可生成定制化的语音音色。当前仅支持 mimo-v2.5-tts-voicedesign 模型。

如何写好音色描述（voice design prompt）
使用 mimo-v2.5-tts-voicedesign 模型时，user 消息中的文本就是音色设计描述。描述越具体、越生动，生成的音色越贴近预期。

关键维度
一条好的音色描述通常涵盖以下多个维度（不需要面面俱到）：

维度|	示例|
性别与年龄|	"young woman in her mid-20s"、"五十多岁的中年男性"|
音色/质感|	"deep and gravelly"、"丝滑醇厚、带着磁性"|
情绪/语气|	"warm and confident"、"温柔但带着一丝疲惫"|
语速/节奏|	"slow and deliberate"、"语速极快，像连珠炮"|


以下维度可选择性加入，增加丰富度：
- 角色/人设：narrator, podcast host, 评书先生, 深夜电台DJ
- 说话风格：casual and colloquial, 一本正经地, 压低嗓音像在密谋
- 场景描写：narrating a nature documentary, 在给投资人路演
- 年代参照：1940s film noir, 八十年代译制片配音



写法建议
- 简洁描述型 -- 用关键词或一句话快速勾勒声音轮廓
```
Heavy Russian accent, gruff middle-aged male, blunt and matter-of-fact.
```
- 专业描述型 -- 通过场景、人设或多维度细节立体刻画声音
```
Young female, extreme close-up with a binaural, ear-to-ear ASMR feel. Audible breathing, subtle swallowing, and soft natural lip sounds. She speaks very slowly, creating a deeply relaxing and immersive experience.
```
```
一位年迈的老先生，说带北方口音的普通话，语速缓慢而沉稳，嗓音略带沙哑和沧桑感，仿佛一位饱经风霜的老爷爷在讲故事，充满岁月的智慧。
```

注意事项
- 长度：1-4 句即可，不需要写长文。核心特征描述清楚比堆砌维度更重要
-避免冲突：不要同时要求矛盾的特征（如"稚嫩的童声 + CEO气场"）
- 避免音质效果词：不要写混响、回声、EQ、压缩等后期处理相关描述
- 避免模糊词：不要用"普通的""正常的""外国的"等缺乏具体指向的描述
- 中英文均可：模型同时支持中英文音色描述，选择你最能精确表达的语言


### 调用示例
非流式调用
```python sdk
import os
from openai import OpenAI
import base64

client = OpenAI(
    api_key=os.environ.get("MIMO_API_KEY"),
    base_url="https://api.xiaomimimo.com/v1"
)

completion = client.chat.completions.create(
    model="mimo-v2.5-tts-voicedesign",
    messages=[
        {
            "role": "user",
            "content": "Give me a young male tone."
        },
        {
            "role": "assistant",
            "content": "Yes, I had a sandwich."
        }
    ],
    audio={
        "format": "wav",
        "optimize_text_preview": True
    }
)

message = completion.choices[0].message
audio_bytes = base64.b64decode(message.audio.data)
with open("audio_file.wav", "wb") as f:
    f.write(audio_bytes)
```

流式调用
注意
mimo-v2.5-tts-voicedesign 的低延迟流式输出功能暂未上线，如有相关需求，请关注近期的功能更新。
流式调用接口目前降级为兼容模式，仅在所有推理完成后以流式格式返回一次结果。
```Python SDK
import base64
import os
import numpy as np
import soundfile as sf
from openai import OpenAI

client = OpenAI(
    api_key=os.environ.get("MIMO_API_KEY"),
    base_url="https://api.xiaomimimo.com/v1"
)

completion = client.chat.completions.create(
    model="mimo-v2.5-tts-voicedesign",
    messages=[
        {
            "role": "user",
            "content": "Give me a young male tone."
        },
        {
            "role": "assistant",
            "content": "You are UN-BE-LIEVABLE! I am sooooo done with your constant lies. GET. OUT!"
        }
    ],
    audio={
        "format": "pcm16",
        "optimize_text_preview": True
    },
    stream=True
)

# 24kHz PCM16LE mono audio
collected_chunks: np.ndarray = np.array([], dtype=np.float32)

for chunk in completion:
    if not chunk.choices:
        continue
    delta = chunk.choices[0].delta
    audio = getattr(delta, "audio", None)

    if audio is not None:
        assert isinstance(audio, dict), f"Expected audio to be a dict, got {type(audio)}"
        pcm_bytes = base64.b64decode(audio["data"])
        np_pcm = np.frombuffer(pcm_bytes, dtype=np.int16).astype(np.float32) / 32768.0
        collected_chunks = np.concatenate((collected_chunks, np_pcm))
        print(f"Received audio chunk of size {len(pcm_bytes)} bytes")

# Save the collected audio to a file
os.makedirs("tmp", exist_ok=True)
sf.write("tmp/output.wav", collected_chunks, samplerate=24000)
print("Audio saved to tmp/output.wav")
```