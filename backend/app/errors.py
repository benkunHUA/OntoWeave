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
