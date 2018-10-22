# escape=`

FROM microsoft/dotnet:2.1-aspnetcore-runtime

ARG SOURCE=VirtoCommerce.Storefront

ADD $SOURCE c:\vc-storefront

WORKDIR /vc-storefront
#Nano server doesn't support installs, need to copy DLLs manually
COPY libs\x64\*.dll c:\windows\SysWOW64\
COPY libs\x86\*.dll c:\windows\System32\
ENTRYPOINT ["dotnet", "VirtoCommerce.Storefront.dll"]