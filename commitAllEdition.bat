@echo off

copy /Y .\client45\src\com\yuchting\yuchberry\client\*.* .\client\src\com\yuchting\yuchberry\client\
copy /Y .\client45\src\com\yuchting\yuchberry\client\*.* .\client46\src\com\yuchting\yuchberry\client\
copy /Y .\client45\src\com\yuchting\yuchberry\client\*.* .\client47\src\com\yuchting\yuchberry\client\
copy /Y .\client45\src\com\yuchting\yuchberry\client\*.* .\client60\src\com\yuchting\yuchberry\client\

copy /Y .\client45\src\local\*.* .\client\src\local\
copy /Y .\client45\src\local\*.* .\client46\src\local\
copy /Y .\client45\src\local\*.* .\client47\src\local\
copy /Y .\client45\src\local\*.* .\client60\src\local\

svn add .\client\src\local\*.*
svn add .\client46\src\local\*.*
svn add .\client47\src\local\*.*
svn add .\client60\src\local\*.*

svn add .\client60\src\com\yuchting\yuchberry\client\*.*
svn add .\client60\src\com\yuchting\yuchberry\client\*.*
svn add .\client60\src\com\yuchting\yuchberry\client\*.*
svn add .\client60\src\com\yuchting\yuchberry\client\*.*

pause