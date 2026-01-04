@echo off
set JLINK_VM_OPTIONS=
set DIR=%~dp0
"%DIR%\java" %JLINK_VM_OPTIONS% -m com.kass.vocalanalysistool/com.kass.vocalanalysistool.controller.Main %*
