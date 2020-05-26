FROM mcr.microsoft.com/dotnet/core/aspnet:3.1

ARG SOURCE=VirtoCommerce.StoreFront

ADD $SOURCE /vc-storefront/

WORKDIR /vc-storefront

ENTRYPOINT ["dotnet", "VirtoCommerce.Storefront.dll"]
