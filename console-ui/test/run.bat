@echo off

if "%1" neq "" (
    npm run singletest %1 %2
) else (
    npm run paralleltest
)
