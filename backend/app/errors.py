class DomainError(Exception):
    """Base class for business rule violations."""


class NotFoundError(DomainError):
    pass


class InvalidTransitionError(DomainError):
    pass


class InvariantViolationError(DomainError):
    pass
