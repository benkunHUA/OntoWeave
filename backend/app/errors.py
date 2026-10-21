class DomainError(Exception):
    """Base class for business rule violations."""


class NotFoundError(DomainError):
    pass


class InvalidTransitionError(DomainError):
    pass


class InvariantViolationError(DomainError):
    pass


class InvalidRequestError(DomainError):
    """请求参数在领域层不合法（映射为 400）。"""


class DataServiceUnavailableError(DomainError):
    """Java 数据服务不可达或返回了非业务错误。"""


class UnprocessableEntityError(DomainError):
    """Java 业务校验未通过（映射为 422），携带原始 details/issues。"""

    def __init__(
        self,
        message: str,
        *,
        code: str | None = None,
        details: dict[str, object] | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.details = details or {}
