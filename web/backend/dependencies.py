"""
FastAPI dependency: extract + validate Bearer token from the request.
Supports both Authorization: Bearer header AND ?token= query param
(the latter is required for SSE / EventSource which cannot set headers).
"""
from fastapi import Depends, HTTPException, Query, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlmodel import Session, select

from auth import decode_token
from database import get_session, Operator

bearer_scheme = HTTPBearer(auto_error=False)


async def get_current_operator(
    credentials: HTTPAuthorizationCredentials = Depends(bearer_scheme),
    token: str = Query(default=None, alias="token"),  # for SSE / EventSource
    session: Session = Depends(get_session),
) -> Operator:
    # Prefer Authorization header; fall back to ?token= query param (SSE)
    actual_token = (credentials.credentials if credentials else None) or token

    if not actual_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Not authenticated",
            headers={"WWW-Authenticate": "Bearer"},
        )

    payload = decode_token(actual_token)

    if payload is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    officer_id: str = payload.get("sub")
    if not officer_id:
        raise HTTPException(status_code=401, detail="Malformed token")

    operator = session.exec(
        select(Operator).where(Operator.officer_id == officer_id)
    ).first()

    if not operator or not operator.is_active:
        raise HTTPException(status_code=401, detail="Operator not found or inactive")

    return operator
