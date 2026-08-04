#!/usr/bin/env bash
# 百炼视觉模型连通性 + OCR 烟测 (不打印 key)。
# 用法:
#   1) 在 src/main/resources/application.properties 填 app.vision.api-key=sk-xxx
#   2) bash scripts/test_vision_ocr.sh
#   或: DASHSCOPE_API_KEY=sk-xxx bash scripts/test_vision_ocr.sh
set -e
cd "$(dirname "$0")/.."

PROPS="src/main/resources/application.properties"
KEY="${DASHSCOPE_API_KEY:-$(awk -F= '/^app.vision.api-key=/{print substr($0,index($0,"=")+1)}' "$PROPS" 2>/dev/null | tr -d '[:space:]')}"
if [ -z "$KEY" ]; then
  echo "no key: set DASHSCOPE_API_KEY or fill app.vision.api-key in $PROPS"
  exit 1
fi
MODEL="${VISION_MODEL:-$(awk -F= '/^app.vision.model=/{print substr($0,index($0,"=")+1)}' "$PROPS" 2>/dev/null | tr -d '[:space:]')}"
[ -z "$MODEL" ] && MODEL="qwen-image-2.0-pro-2026-06-22"
BASE="${VISION_BASE:-https://dashscope.aliyuncs.com/compatible-mode/v1}"

# 1x1 透明 PNG (验证连通 + 鉴权 + 模型名 + 接口格式; OCR 效果请换真实扫描件 data URL)
IMG="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

echo "=== 百炼视觉模型烟测 ==="
echo "base=$BASE model=$MODEL key=<<hidden len=${#KEY}>"
echo "--- request ---"
curl -s -w "\n--- http=%{http_code} time=%{time_total}s ---\n" \
  "$BASE/chat/completions" \
  -H "Authorization: Bearer $KEY" \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"$MODEL\",\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"这张图片里有什么?请描述。\"},{\"type\":\"image_url\",\"image_url\":{\"url\":\"$IMG\"}}]}]}"
