import ast
from pathlib import Path

import pytest

BACKEND_DIR = Path(__file__).resolve().parents[1]
RUNTIME_DIR = BACKEND_DIR / "app" / "agent" / "runtime"
STREAM_PATH = RUNTIME_DIR / "stream.py"
SUBAGENT_TRACE_PATH = RUNTIME_DIR / "subagent_trace.py"
FORBIDDEN_MODULES = (
    "app.agent.tools",
    "app.services.changeset",
    "app.services.view_state",
)


def _module_name(path: Path) -> str:
    parts = list(path.relative_to(BACKEND_DIR).with_suffix("").parts)
    if parts[-1] == "__init__":
        parts.pop()
    return ".".join(parts)


def _imports_from_source(source: str, module_name: str) -> set[str]:
    """解析源码里的绝对 / 相对导入，统一还原成完整模块名。"""
    package_parts = module_name.split(".")[:-1]
    modules: set[str] = set()
    for node in ast.walk(ast.parse(source)):
        if isinstance(node, ast.Import):
            modules.update(alias.name for alias in node.names)
        elif isinstance(node, ast.ImportFrom):
            if node.level:
                base = package_parts[: len(package_parts) - (node.level - 1)]
                base_module = ".".join([*base, *([node.module] if node.module else [])])
            else:
                base_module = node.module or ""
            if base_module:
                modules.add(base_module)
            for alias in node.names:
                if alias.name == "*":
                    continue
                modules.add(f"{base_module}.{alias.name}" if base_module else alias.name)
    return modules


def _imported_modules(path: Path) -> set[str]:
    return _imports_from_source(path.read_text(encoding="utf-8"), _module_name(path))


def _matches(module: str, forbidden: str) -> bool:
    return module == forbidden or module.startswith(f"{forbidden}.")


def test_import_parser_expands_from_import_aliases():
    source = """
from app.services import changeset
from app.agent import tools
from . import tools
from .. import tools
"""

    modules = _imports_from_source(source, "app.agent.runtime.stream")

    assert "app.services.changeset" in modules
    assert "app.agent.tools" in modules
    assert "app.agent.runtime.tools" in modules


@pytest.mark.parametrize("path", [STREAM_PATH, SUBAGENT_TRACE_PATH])
@pytest.mark.parametrize("forbidden", FORBIDDEN_MODULES)
def test_runtime_has_no_domain_imports(path, forbidden):
    modules = _imported_modules(path)

    assert not any(_matches(module, forbidden) for module in modules)
