FROM mcr.microsoft.com/dotnet/core/aspnet:2.2

ARG SOURCE=VirtoCommerce.StoreFront

ADD $SOURCE /vc-storefront/

WORKDIR /vc-storefront

ENTRYPOINT ["dotnet", "VirtoCommerce.Storefront.dll"]
