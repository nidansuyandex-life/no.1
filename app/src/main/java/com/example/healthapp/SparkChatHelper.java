package com.example.healthapp;

import android.util.Log;
import android.webkit.WebView;

import com.iflytek.sparkchain.core.LLM;
import com.iflytek.sparkchain.core.LLMCallbacks;
import com.iflytek.sparkchain.core.LLMConfig;
import com.iflytek.sparkchain.core.LLMError;
import com.iflytek.sparkchain.core.LLMFactory;
import com.iflytek.sparkchain.core.LLMResult;
import com.iflytek.sparkchain.core.SparkChain;
import com.iflytek.sparkchain.core.SparkChainConfig;

public class SparkChatHelper {

    private static final String TAG = "SparkChatHelper";

    private static final String APP_ID = "4c627b59";
    private static final String API_KEY = "2fbffaacd145309be7c024db99e9c7ef";
    private static final String API_SECRET = "YzI1MDJmYWM0NTliNzNkMjI3NGIyM2Uz";

    private static final String SYS_PROMPT_CHAT =
        "你是用户的私人健康生活助手。用户会提供【用户当前数据】和【用户问题】两部分内容。" +
        "请结合数据回答，给具体、可执行的建议。语气友好、简洁，不要泛泛而谈。" +
        "中文回答，不超过 250 字。涉及医疗、用药、严重皮肤问题时提醒用户就医，不要给出诊断。";

    private static final String SYS_PROMPT_STYLING =
        "你是专业时尚搭配师。用户会提供衣橱清单和场景要求。" +
        "严格按要求只返回 JSON，不要加任何解释、代码块标记或 markdown。" +
        "JSON 格式必须是：{\"reason\":\"一句话理由\",\"items\":[{\"cat\":\"top\",\"name\":\"单品名\"}]}。" +
        "cat 只能取 top/pants/dress/coat/shoes/bag/acc；name 必须与用户给的清单里完全一致。";

    private final WebView webView;
    private LLM chatLlm;
    private boolean sdkInited = false;

    public SparkChatHelper(WebView webView) {
        this.webView = webView;
        try {
            SparkChainConfig config = SparkChainConfig.builder()
                    .appID(APP_ID)
                    .apiKey(API_KEY)
                    .apiSecret(API_SECRET)
                    .logLevel(Log.INFO);
            int ret = SparkChain.getInst().init(webView.getContext(), config);
            sdkInited = (ret == 0);
            if (!sdkInited) Log.e(TAG, "SparkChain init failed, code=" + ret);
        } catch (Exception e) {
            Log.e(TAG, "SparkChain init exception", e);
        }
        initLlm();
    }

    private void initLlm() {
        try {
            LLMConfig llmConfig = LLMConfig.builder()
                    .domain("4.0Ultra")
                    .url("wss://spark-api.xf-yun.com/v4.0/chat")
                    .maxTokens(8192);
            chatLlm = LLMFactory.textGeneration(llmConfig);
            if (chatLlm == null) Log.e(TAG, "LLM instance creation failed.");
        } catch (Exception e) {
            Log.e(TAG, "initLlm exception", e);
        }
    }

    public void chat(String question, String tag) {
        if (chatLlm == null) {
            callbackError("LLM 未初始化", tag);
            return;
        }
        final String ftag = (tag == null || tag.isEmpty()) ? "chat" : tag;
        String sysPrompt = "styling".equals(ftag) ? SYS_PROMPT_STYLING : SYS_PROMPT_CHAT;
        String fullPrompt = "【系统指令】" + sysPrompt + "\n\n" + question;

        try {
            chatLlm.arun(fullPrompt, new LLMCallbacks() {
                @Override
                public void onLLMResult(LLMResult llmResult) {
                    String content = llmResult.getContent();
                    if (content != null && !content.isEmpty()) {
                        callbackResult(content, ftag);
                    }
                    if (llmResult.getStatus() == 2) {
                        callbackState("finished", ftag);
                    }
                }

                @Override
                public void onLLMError(LLMError llmError) {
                    callbackError(llmError.getErrMsg(), ftag);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "chat exception", e);
            callbackError("调用异常：" + e.getMessage(), ftag);
        }
    }

    public void destroy() {
        try {
            if (sdkInited) SparkChain.getInst().unInit();
        } catch (Exception ignored) {}
        chatLlm = null;
    }

    private void callbackResult(String text, String tag) {
        evalJs("window.onNativeChatResult && window.onNativeChatResult(" + jsString(text) + ", " + jsString(tag) + ");");
    }
    private void callbackError(String msg, String tag) {
        evalJs("window.onNativeChatError && window.onNativeChatError(" + jsString(msg) + ", " + jsString(tag) + ");");
    }
    private void callbackState(String state, String tag) {
        evalJs("window.onNativeChatState && window.onNativeChatState(" + jsString(state) + ", " + jsString(tag) + ");");
    }
    private void evalJs(String code) {
        try {
            if (webView != null) webView.post(() -> webView.evaluateJavascript(code, null));
        } catch (Exception e) {
            Log.e(TAG, "evalJs error", e);
        }
    }
    private static String jsString(String s) {
        if (s == null) return "''";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "") + "'";
    }
}