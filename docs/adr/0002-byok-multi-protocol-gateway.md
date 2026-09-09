# ADR-0002：本机 BYOK 与多协议 AI 网关

- 状态：已接受
- 日期：2026-09-09

## 背景

图片识别和评分既需要百度手写 OCR，也需要能接收图片的通用视觉 LLM；用户还需要自行选择提供商和模型。

## 决策

在 Android 端建立统一的 AI 网关抽象，支持 OpenAI Responses、OpenAI-compatible Chat Completions、Anthropic Messages、Gemini `generateContent` 和百度手写 OCR。提供商配置、阶段绑定和提示词模板分别持久化；只允许 HTTPS；每个阶段可单独取消和重试，不自动换服务。

## 后果

应用不需要后端账号或中转服务，但用户要自行提供密钥、承担服务费用和兼容性风险。结构化 JSON 优先，解析失败时保留原始响应；协议错误、认证、限流、余额、超时和格式错误必须显示为可理解的阶段状态。

