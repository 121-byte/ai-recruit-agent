#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
简历解析脚本 (markitdown 主路径)。

由 FileParserUtil.parseWithMarkitdown 以如下方式调用：
    python3 scripts/parse_resume.py <临时文件绝对路径>

职责：用微软 markitdown 将传入的文件 (PDF/DOCX/DOC/PPT/XLS/HTML 等) 转为
Markdown 文本并打印到 stdout，供 Java 端 BufferedReader 读取。

约定：
- 成功：退出码 0，stdout 输出转换后的 Markdown 文本。
- 失败：退出码非 0，stdout 为空 (错误信息写到 stderr)，Java 端检测到
  `code != 0 || 输出为空` 后自动回退到 PDFBox/POI 兜底，保证上传链路不中断。

依赖：在所用的 python 环境中安装 markitdown
    pip install 'markitdown[all]'
未安装时本脚本会在 import 阶段失败并退出码 1，等价于走兜底。
"""

import sys


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: parse_resume.py <file-path>", file=sys.stderr)
        return 2

    file_path = sys.argv[1]
    if not file_path:
        print("empty file path", file=sys.stderr)
        return 2

    # Windows 控制台默认编码可能是 GBK/Cp936，统一用 UTF-8 输出，避免中文乱码。
    try:
        sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    except Exception:
        pass

    try:
        from markitdown import MarkItDown
    except Exception as e:  # 未安装 markitdown
        print(f"markitdown not available: {e}", file=sys.stderr)
        return 1

    try:
        md = MarkItDown()
        result = md.convert(file_path)
        text = result.text_content if result is not None else ""
    except Exception as e:  # 转换失败 (损坏文件/不支持的格式等)
        print(f"markitdown convert failed for {file_path}: {e}", file=sys.stderr)
        return 1

    if text is None or not text.strip():
        print("markitdown produced empty output", file=sys.stderr)
        return 1

    sys.stdout.write(text)
    if not text.endswith("\n"):
        sys.stdout.write("\n")
    sys.stdout.flush()
    return 0


if __name__ == "__main__":
    sys.exit(main())
