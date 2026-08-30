// command-module 은 hexagonal 4층(core/application/adapter/infrastructure)의
// 집합 프로젝트(aggregator)다. 자체 소스가 없으므로 아티팩트를 만들지 않는다.
tasks.named("bootJar") { enabled = false }
tasks.named("jar") { enabled = false }
