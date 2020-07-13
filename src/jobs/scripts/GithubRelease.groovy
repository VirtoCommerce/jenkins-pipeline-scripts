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
        for(def i=1; i < 6; i++) {
            def releasesUrl = "https://api.github.com/repos/${repoOrg}/${repoName}/releases?page=${i}"
            def response = context.httpRequest authentication: "vc-ci", httpMode: 'GET', responseHandle: 'STRING', url: releasesUrl
            def content = response.content
            def releases = new groovy.json.JsonSlurperClassic().parseText(content)
            for (release in releases) {
                if (release.tag_name.contains(tagContains) && release.prerelease == prerelease) {
                    context.echo "Release id: ${release.id}"
                    return new GithubRelease(release)
                }
            }
        }
        throw new Exception("No github releases found")
    }


    def static GithubRelease getLatestGithubReleaseRegexp(context, repoOrg, repoName, regexp = /^[013-9]\.\d{1,3}\.\d{1,3}[\s-]{0,1}([a-zA-Z]{2}\d+){0,1}/)
    {
        def releasesUrl = "https://api.github.com/repos/${repoOrg}/${repoName}/releases"
        def response = context.httpRequest authentication:"vc-ci", httpMode:'GET', responseHandle: 'STRING', url:releasesUrl
        def content = response.content
        def releases = new groovy.json.JsonSlurperClassic().parseText(content)
        for(release in releases){
            if(isMatch(release.name, regexp)){
                context.echo "Release id: ${release.id}"
                return new GithubRelease(release)
            }
        }
        throw new Exception("No github releases found")
    }

    def static GithubRelease getLatestGithubReleaseV3(context, repoOrg, repoName, prerelease = true)
    {
        def releasesUrl = "https://api.github.com/repos/${repoOrg}/${repoName}/releases"
        def response = context.httpRequest authentication:"vc-ci", httpMode:'GET', responseHandle: 'STRING', url:releasesUrl
        def content = response.content
        def releases = new groovy.json.JsonSlurperClassic().parseText(content)
        for(release in releases){
            if(!release.name.startsWith('v2') && release.prerelease == prerelease){
                context.echo "Release id: ${release.id}"
                return new GithubRelease(release)
            }
        }
        throw new Exception("No github releases found")
    }

    @NonCPS
    def static boolean isMatch(text, pattern)
    {
        return text ==~ pattern
    }

    def static downloadGithubRelease(context, url, outFile){
        context.httpRequest authentication:"vc-ci", acceptType: 'APPLICATION_OCTETSTREAM', httpMode: 'GET', outputFile: outFile, responseHandle: 'NONE', url: url
    }
}