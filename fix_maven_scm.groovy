SVN_CMD = "C:\\Apps\\TortoiseSVN\\bin\\svn.exe"
MVN_CMD = "C:\\Apps\\apache-maven-3.0.4\\bin\\mvn.bat"

recurseProject(new File('C:\\Repos\\kim')) { if (!isScmCorrect(it)) fixScm(it) }

def recurseProject(File dir, Closure closure) {
  closure.call(dir)
  modules(new File(dir, 'pom.xml')).each { module -> recurseProject(new File(dir, module), closure) }
}

def modules(pom) {
  def pomXml = new XmlSlurper().parse(pom)
  pomXml.modules.module.collect { it.text() }
}

def svnUrl(dir) {
  def cmd = "${SVN_CMD} info ${dir}"
  def proc = cmd.execute()
  proc.waitFor()
  def urlPrefix = 'URL: '
  proc.in.text.readLines().find { it.startsWith(urlPrefix) }.replace(urlPrefix, '')
}

def effectivePom(dir) {
  def pom = new File(dir, 'pom.xml')
  def effectivePom = File.createTempFile('effective-pom-', '.xml')
  try {
    def cmd = "cmd /c ${MVN_CMD} -f ${pom} help:effective-pom -Doutput=${effectivePom}"
    def proc = cmd.execute()
    proc.consumeProcessOutput(System.out, System.err)
    proc.waitFor()
    new XmlSlurper().parse(effectivePom)
  } finally {
    effectivePom.delete()  
  }
}

def isScmCorrect(dir) {
  def effectivePom = effectivePom(dir)
  def svnUrl = svnUrl(dir)
  def connection = effectivePom.scm.connection.text()
  def developerConnection = effectivePom.scm.developerConnection.text()
  def url = effectivePom.scm.url.text()
  if ("scm:svn:${svnUrl}" != connection) return false
  if ("scm:svn:${svnUrl}" != developerConnection) return false
  if (svnUrl != url) return false
  return true
}

def fixScm(dir) {
  def svnUrl = svnUrl(dir)
  def pom = new File(dir, 'pom.xml')
  def pomText = pom.text
  pomText = pomText.replaceFirst('(<name>.*</name>)') { it[0] + """

\t<scm>
\t\t<connection>scm:svn:${svnUrl}</connection>
\t\t<developerConnection>scm:svn:${svnUrl}</developerConnection>
\t\t<url>${svnUrl}</url>
\t</scm>"""
  }
  pom.text = pomText
}