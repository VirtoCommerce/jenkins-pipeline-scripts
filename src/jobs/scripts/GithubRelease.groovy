package jobs.scripts

class GithubRelease{
    long id
    String name
    boolean prerelease
    String tag_name
    String created_at
    String published_at
    List<Map> assets

    GithubRelease(json){
        id = json.id
        name = json.name
        prerelease = json.prerelease
        tag_name = json.tag_name
        created_at = json.created_at
        published_at = json.published_at
        assets = json.assets
    }

    def static GithubRelease getLatestGithubRelease(context, repoOrg, repoName, tagContains='', prerelease = false){
        def releasesUrl = "https://api.github.com/repos/${repoOrg}/${repoName}/releases"
        def response = context.httpRequest authentication:"vc-ci", httpMode:'GET', responseHandle: 'STRING', url:releasesUrl
        def content = response.content
        def releases = new groovy.json.JsonSlurperClassic().parseText(content)
        for(release in releases){
            if(release.tag_name.contains(tagContains) && release.prerelease == prerelease){
                context.echo "Release id: ${release.id}"
                return new GithubRelease(release)
            }
        }
        throw new Exception("No github releases found")
    }
    def static downloadGithubRelease(context, url, outFile){
        context.httpRequest authentication:"vc-ci", acceptType: 'APPLICATION_OCTETSTREAM', httpMode: 'GET', outputFile: outFile, responseHandle: 'NONE', url: url
    }
}