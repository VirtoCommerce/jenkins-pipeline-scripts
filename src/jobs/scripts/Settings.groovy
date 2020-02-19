package jobs.scripts

import groovy.json.JsonSlurperClassic

class Settings implements Serializable
{
    private Object _settings
    private String _environment
    private String _region
    Settings(String json){
        _settings = new JsonSlurperClassic().parseText(json)
    }
    def getAt(String item)
    {
        if(_region == null)
            throw new Exception("Settings error: Region is not set")
        if(_environment == null)
            throw new Exception("Settings error: Environment is not set")
        if((item == 'approvers' || item == 'approvers_e2e') & !_settings.containsKey(item))
            return ''
        if(_environment.startsWith('PR-')){
            return ''
        }
        return _settings[_region][_environment][item]
    }
    def setRegion(String region)
    {
        _region = region
    }
    def setEnvironment(String environment)
    {
        _environment = environment
    }
    def getRegions()
    {
        return _settings.keySet() as String[]
    }
    def getEnvironments(String region = null)
    {
        if(region == null && _region.trim()){
            return _settings[_region].keySet() as String[]
        }
        return _settings[region].keySet() as String[]
    }
    def containsRegion(String region)
    {
        return _settings.containsKey(region)
    }
}