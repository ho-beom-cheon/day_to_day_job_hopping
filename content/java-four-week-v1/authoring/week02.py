lesson(6,'List와 순서 있는 데이터','인덱스·검색·삽입 비용과 목록의 변경 가능성을 구분한다.', '''List는 순서가 있고 중복 원소를 허용합니다. 인덱스는 0부터 시작하며 마지막 유효 인덱스는 size-1입니다. ArrayList의 인덱스 접근은 일정 시간이지만 중간 삽입·삭제에는 뒤 원소 이동이 필요합니다. add의 끝 추가는 보통 분할 상환 상수 시간으로 설명하며, 용량 확장 때 복사가 일어날 수 있습니다. LinkedList도 인덱스 접근은 앞에서 찾아가야 하므로 무조건 빠른 대체재가 아닙니다.

`remove(int)`는 위치를 지우고 `remove(Object)`는 처음 일치하는 값을 지웁니다. Integer 목록에서 remove(1)과 remove(Integer.valueOf(1))을 혼동하지 마세요. contains는 동등성으로 원소를 찾으며 일반적인 ArrayList에서는 순차 검색합니다. 중복과 순서를 보존할지 먼저 정한 뒤 자료구조를 선택합니다.

Arrays.asList는 배열에 연결된 고정 크기 목록으로 set은 가능하지만 add/remove는 지원하지 않습니다. List.of는 변경 불가능한 목록입니다. subList는 원본과 연결된 뷰이므로 독립 작업 목록이 필요하면 복사합니다. 반복 중 직접 삭제하는 대신 removeIf나 반복자의 remove 계약을 사용합니다.''', '''var values = new ArrayList<>(List.of(1, 2, 1));
values.remove(Integer.valueOf(1));
if (!values.equals(List.of(2, 1))) throw new AssertionError();
values.remove(1);
if (!values.equals(List.of(2))) throw new AssertionError();
values.add(3); values.removeIf(n -> n % 2 == 0);
if (!values.equals(List.of(3))) throw new AssertionError();''','순서를 유지하는 최근 검색어 목록을 구현하세요. 같은 검색어를 다시 넣으면 기존 위치에서 제거한 뒤 맨 앞으로 옮기고 최대 5개를 보존하세요.','중복 입력 후 목록에 같은 값이 하나만 남는다.^최대 크기를 넘으면 가장 오래된 값이 제거된다.^빈 목록과 1개 목록의 인덱스 경계를 테스트한다.','List는 순서와 중복을 지원한다.^인덱스 삭제와 값 삭제를 구분한다.^뷰·고정 크기·불변 목록은 다르다.','java/util/ArrayList.html', '''원소가 3개인 List의 마지막 유효 인덱스는? ^ 2 ^ 3 ^ 1 ^ 인덱스는 0부터 시작해 size-1까지다.
List가 일반적으로 허용하는 특성은? ^ 순서와 중복 ^ 중복 금지와 정렬 강제 ^ 키로만 조회 ^ List는 위치 기반 컬렉션이다.
ArrayList의 중간 원소 삭제가 비쌀 수 있는 이유는? ^ 뒤 원소를 이동해야 한다 ^ 항상 네트워크를 쓴다 ^ 모든 원소를 정렬한다 ^ 배열의 연속 위치를 유지하려고 뒤쪽 참조를 이동한다.
List<Integer>에서 remove(1)은? ^ 인덱스 1의 원소를 삭제 ^ 값 1을 모두 삭제 ^ 마지막 원소만 삭제 ^ int 인수를 받는 위치 기반 오버로드가 선택된다.
값 1을 하나 삭제하려면? ^ remove(Integer.valueOf(1)) ^ remove(1) ^ clear(1) ^ Object 오버로드가 처음 일치하는 값을 찾는다.
ArrayList.contains의 일반적인 검색 비용은? ^ 원소 수에 비례할 수 있다 ^ 반드시 O(1)이다 ^ 항상 O(log n)이다 ^ 정렬·해시 인덱스 없이 순차 비교한다.
Arrays.asList 결과에서 가능한 작업은? ^ 기존 위치의 set ^ add로 크기 증가 ^ remove로 크기 감소 ^ 고정 크기 뷰이며 원소 교체는 지원한다.
List.of 결과를 수정하면? ^ 지원하지 않는 변경 예외 ^ 원본 배열이 수정된다 ^ 자동 가변 전환 ^ List.of는 변경 불가능한 목록이다.
subList로 독립적인 작업 스냅샷을 만들려면? ^ 새 ArrayList로 복사 ^ 반환 뷰를 그대로 저장만 ^ 원본의 clear 호출 ^ subList는 원본과 연결된 뷰라 복사와 다르다.
조건에 맞는 항목들을 안전하게 제거하기 좋은 메서드는? ^ removeIf ^ for-each 안에서 목록 remove 반복 ^ get(-1) ^ removeIf는 컬렉션의 조건부 제거 계약을 사용한다.
LinkedList에서 임의 인덱스로 반복 조회하면? ^ 이동 비용이 누적될 수 있다 ^ 배열처럼 항상 O(1)이다 ^ 컴파일 오류다 ^ 연결 목록은 해당 위치까지 노드를 따라갈 수 있다.
ArrayList 끝 추가의 분할 상환 O(1)은 무엇을 뜻하는가? ^ 여러 연산의 평균 비용이며 일부 확장은 비싸다 ^ 모든 추가가 같은 시간이다 ^ 어떤 크기든 메모리를 쓰지 않는다 ^ 용량 확장 시 복사 비용을 여러 추가 연산에 나누어 해석한다.''')
lesson(7,'Set과 Map의 키 설계','중복 제거와 키 기반 집계를 올바른 컬렉션으로 표현한다.', '''Set은 중복 없는 원소 집합이고 Map은 키와 값의 연결입니다. HashSet과 HashMap의 동등성은 equals/hashCode 계약에 기대므로 1주차의 불변 키 설계가 그대로 적용됩니다. HashMap은 순회 순서를 약속하지 않습니다. 입력 순서를 보여줘야 하면 LinkedHashMap 같은 명시적 순서 계약을 선택하세요.

Map.get이 null이면 키가 없는 경우와 null 값이 저장된 경우를 구분하기 어려울 수 있습니다. 해당 구현의 null 정책을 확인하고 필요하면 containsKey를 사용합니다. Map.of는 null과 중복 키를 허용하지 않습니다. 빈 Map을 반환하는 것과 null을 반환하는 것도 호출자에게 다른 계약입니다.

빈도 집계는 `merge(key, 1, Integer::sum)`로 간결하게 표현할 수 있습니다. 하지만 일반 HashMap을 여러 스레드가 동시에 변경해도 안전하다는 뜻은 아닙니다. Map 전체를 다루는 복합 연산은 별도 동시성 정책이 필요합니다. 제거·덮어쓰기·기본값이 각 도메인에서 어떤 의미인지 작은 예제로 검증합니다.''', '''Map<String,Integer> counts = new LinkedHashMap<>();
for (String word : List.of("red","blue","red")) counts.merge(word,1,Integer::sum);
if (counts.get("red")!=2 || counts.size()!=2) throw new AssertionError();
if (!new ArrayList<>(counts.keySet()).equals(List.of("red","blue"))) throw new AssertionError();
if (new HashSet<>(List.of("a","a","b")).size()!=2) throw new AssertionError();''','상품 코드별 조회 횟수를 집계하고 처음 나타난 순서대로 출력하세요. 중복 제거와 빈도 집계가 다른 작업임을 결과로 설명하세요.','같은 키의 횟수가 누적된다.^처음 등장한 키의 순서를 유지한다.^빈 입력은 빈 결과를 반환한다.','Set은 중복 제거, Map은 연결을 표현한다.^HashMap의 순서를 추측하지 않는다.^단일 메서드의 간결함과 동시성 안전성은 별개다.','java/util/Map.html', '''같은 값을 한 번만 보관할 때 기본 추상화는? ^ Set ^ List의 중복 허용만 사용 ^ Thread ^ 집합은 동등한 원소의 중복을 허용하지 않는다.
키별 조회 횟수를 저장하기에 맞는 추상화는? ^ Map ^ 값 없는 Set만 ^ StringBuilder ^ 키와 누적 값을 연결해야 한다.
HashMap의 순회 순서는? ^ 일반적으로 보장되지 않는다 ^ 반드시 삽입 순서다 ^ 반드시 키 정렬 순서다 ^ 구현의 우연한 순서를 계약으로 사용하면 안 된다.
삽입 순서를 보존하는 Map 구현은? ^ LinkedHashMap ^ HashMap의 기본 계약 ^ IdentityHashMap의 기본 계약 ^ LinkedHashMap은 기본적으로 삽입 순회 순서를 제공한다.
Map.get의 결과가 null일 때 확인할 수 있는 것은? ^ 키 부재와 null 값을 구분하려면 추가 확인이 필요하다 ^ 반드시 키가 없다 ^ 반드시 키가 있다 ^ null 허용 구현에서는 containsKey가 구분에 도움이 된다.
Map.of에 중복 키를 넣으면? ^ IllegalArgumentException ^ 마지막 값만 조용히 남는다 ^ 값 목록으로 자동 병합 ^ 불변 Map 팩토리는 중복 키를 거부한다.
merge(word,1,Integer::sum)의 집계 의도는? ^ 없으면 1, 있으면 더한다 ^ 항상 1로 덮어쓴다 ^ 키를 정렬한다 ^ 기본 값과 결합 함수를 이용한 빈도 집계다.
HashSet에서 두 동등한 키가 중복으로 남는다면 먼저 볼 계약은? ^ equals와 hashCode ^ toString 길이 ^ 변수 이름 ^ 해시와 동등성의 불일치가 중복 판정을 깨뜨릴 수 있다.
HashMap.merge를 썼다는 것만으로 보장되지 않는 것은? ^ 여러 스레드의 안전한 공유 변경 ^ 값 집계 표현 ^ 한 호출의 키 선택 ^ 일반 HashMap은 동시 변경을 지원하는 자료구조가 아니다.
Set에 a,a,b를 넣은 결과 크기는? ^ 2 ^ 3 ^ 1 ^ 동등한 a를 하나로 취급한다.
처음 등장한 순서가 요구사항인데 HashMap을 선택했다. 필요한 수정은? ^ 순서 보장 구현을 선택한다 ^ 테스트에서 우연한 순서만 기대한다 ^ 해시를 상수로 바꾼다 ^ 요구하는 순서를 자료구조 계약에 직접 반영해야 한다.
Map.of("a", null)은 어떤 결과인가? ^ NullPointerException ^ null 값 저장 성공 ^ 빈 Map 반환 ^ Map.of는 null 키와 값을 허용하지 않는다.''')
lesson(8,'제네릭과 타입 경계','타입 불변성과 와일드카드의 읽기·쓰기 제약을 이해한다.', '''제네릭은 컬렉션에서 꺼낸 값을 매번 형변환하는 실수를 줄입니다. `List<Integer>`는 `List<Number>`의 하위 타입이 아닙니다. 만약 그렇게 허용하면 Number 목록에 Double을 넣는 코드가 원래 Integer 목록을 오염시킬 수 있습니다. 이를 타입 불변성으로 설명합니다.

`? extends Number`는 어떤 Number 하위 타입의 목록을 읽을 때 사용합니다. 정확히 어떤 하위 타입인지는 모르므로 Integer 같은 비-null 값을 안전하게 추가할 수 없습니다. `? super Integer`는 Integer를 받아들이는 목록으로 사용할 수 있지만 꺼낸 값을 Integer라고 단정할 수 없어서 Object로 읽습니다. 생산자는 extends, 소비자는 super라는 요약은 데이터 흐름을 먼저 파악한 뒤 적용하세요.

raw type을 쓰면 컴파일러가 제공하던 검사가 줄어 런타임 ClassCastException으로 문제가 미뤄집니다. 제네릭 타입 소거 때문에 런타임에 `List<String>`의 원소 타입을 완전하게 검사하는 instanceof는 사용할 수 없습니다. 코드 예제는 Number 생산자에서 값을 읽고 Integer 소비자에 값을 넣는 두 경계를 분리합니다.''', '''List<Integer> ints = List.of(1,2,3);
List<? extends Number> producer = ints;
double sum = 0; for (Number n : producer) sum += n.doubleValue();
List<? super Integer> consumer = new ArrayList<Number>();
consumer.add(4); Object value = consumer.get(0);
if (sum!=6 || !value.equals(4)) throw new AssertionError();''','Number 목록의 합계를 구하는 함수와 Integer를 목적 목록에 복사하는 함수를 작성하세요. 읽기와 쓰기 위치에 따라 와일드카드를 선택하고 컴파일되지 않는 예제를 주석으로 설명하세요.','`List<Integer>`와 `List<Double>` 모두 읽기 입력으로 사용할 수 있다.^Integer를 `List<Number>` 목적지에 복사한다.^raw type과 불필요한 형변환을 사용하지 않는다.','제네릭 매개변수는 기본적으로 불변이다.^extends는 읽기, super는 쓰기 경계를 표현한다.^raw type은 검사 누락을 일으킨다.','https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html', '''List<Integer>를 List<Number>에 바로 대입할 수 있는가? ^ 없다 ^ 항상 있다 ^ 비어 있을 때만 컴파일된다 ^ 제네릭 타입은 기본적으로 불변이다.
List<? extends Number>에서 읽은 원소의 안전한 타입은? ^ Number ^ 반드시 Integer ^ 반드시 Double ^ 상한이 Number이므로 그 타입으로 읽을 수 있다.
List<? extends Number>에 Integer 1을 추가하는 코드는? ^ 일반적으로 컴파일되지 않는다 ^ 항상 안전하다 ^ 모든 원소를 정수로 바꾼다 ^ 실제 목록이 Double 전용일 수도 있어 비-null 추가를 허용하지 않는다.
List<? super Integer>에 안전하게 추가할 수 있는 비-null 값은? ^ Integer ^ Double ^ String ^ Integer 또는 그 상위 타입을 받는 목적지이므로 Integer를 넣을 수 있다.
List<? super Integer>에서 꺼낸 값의 보편적인 안전 타입은? ^ Object ^ Integer로 확정 ^ Number로 확정 ^ 실제 목록이 Object 목록일 수 있어 기존 원소 타입을 단정할 수 없다.
raw List를 사용하면 어떤 위험이 커지는가? ^ 타입 오류가 실행 시점으로 밀린다 ^ 모든 타입 검사가 강화된다 ^ 자동 깊은 복사가 수행된다 ^ 제네릭 검사 없이 혼합 타입을 넣을 수 있다.
제네릭 불변성이 막는 예시는? ^ Integer 목록에 Double이 섞이는 경로 ^ 모든 목록의 순회 ^ 정수 덧셈 ^ List<Integer>를 List<Number>로 허용하면 Number 추가가 안전하지 않다.
생산자 extends라는 요약에서 생산자는? ^ 호출자가 값을 읽어 가져오는 쪽 ^ 무조건 값을 추가하는 쪽 ^ 객체를 생성하는 모든 생성자 ^ 타입을 제공하는 읽기 원천을 뜻한다.
소비자 super라는 요약에서 소비자는? ^ 값을 받아 저장하는 목적지 ^ 값을 반환만 하는 함수 ^ 어떤 Map 키든 ^ 전달하는 값의 상위 타입 목록도 목적지가 될 수 있다.
instanceof List<String>으로 원소 타입까지 검사할 수 있는가? ^ 없다 ^ 모든 경우 가능하다 ^ 빈 목록에만 가능하다 ^ 타입 소거로 런타임의 구체적 제네릭 정보가 제한된다.
List<Double>을 받는 합계 함수에 적절한 매개변수는? ^ List<? extends Number> ^ List<Integer> ^ List<Object>만 ^ Number 하위 타입 원소를 읽는 함수에 상한 와일드카드가 맞다.
Integer 복사의 목적지가 List<Object>여도 되게 하려면? ^ List<? super Integer> ^ List<? extends Integer> ^ raw List만 ^ 소비하는 값보다 넓은 타입을 허용하는 하한 와일드카드를 사용한다.''')
lesson(9,'스트림과 부수 효과','지연 실행·변환·집계를 분리해 읽기 쉬운 파이프라인을 만든다.', '''Stream은 데이터를 저장하는 컬렉션이 아니라 원천의 데이터를 처리하는 흐름입니다. filter와 map 같은 중간 연산은 지연 실행되며 count, reduce, toList 같은 최종 연산이 실행을 이끕니다. filter는 조건에 맞는 원소를 남기고 map은 각 원소를 다른 값으로 변환합니다. 단일 Stream을 소비한 뒤 다시 사용하지 말고 원천에서 새 Stream을 만드세요.

외부 List에 add하는 부수 효과로 집계하면 병렬화나 재사용 때 안전성을 판단하기 어려워집니다. collect나 reduce처럼 결과를 명시적으로 반환하는 연산을 사용합니다. parallelStream은 자동 성능 개선 스위치가 아닙니다. 작업 분할 비용, 원천 크기, 공유 상태, I/O 대기 특성을 따져야 합니다.

`Stream.toList()`는 변경 불가능한 결과를 반환합니다. 가변 결과가 필요하면 그 의도를 별도로 표현하세요. peek는 핵심 업무 처리를 맡길 장소가 아닙니다. 최적화와 단축 평가로 기대한 호출이 모두 일어나지 않을 수 있습니다. 순서가 중요한 결과는 원천과 연산의 순서 계약을 확인합니다.''', '''var result = List.of(1,2,3,4).stream().filter(n -> n%2==0).map(n -> n*10).toList();
if (!result.equals(List.of(20,40))) throw new AssertionError();
int sum = List.of(1,2,3).stream().reduce(0,Integer::sum);
if (sum!=6) throw new AssertionError();
try { result.add(60); throw new AssertionError(); }
catch (UnsupportedOperationException expected) { }''','가격 목록에서 음수를 제외하고 부가세 적용 전 합계를 계산하세요. 빈 목록의 결과를 정하고 외부 변수를 변경하지 않는 파이프라인으로 구현하세요.','filter와 map의 역할을 예제에서 구분한다.^빈 입력과 전부 제외되는 입력의 결과를 검증한다.^외부 가변 컬렉션에 부수 효과로 결과를 쌓지 않는다.','중간 연산은 지연 실행된다.^Stream은 한 번 소비한다.^병렬화 전에 부수 효과와 비용을 검토한다.','java/util/stream/Stream.html', '''filter의 역할은? ^ 조건에 맞는 원소를 남긴다 ^ 모든 원소를 정렬한다 ^ 원천을 직접 삭제한다 ^ 필터는 결과 흐름에 포함할 원소를 판정한다.
map의 역할은? ^ 원소를 다른 값으로 변환한다 ^ 반드시 크기를 늘린다 ^ Map 자료구조만 만든다 ^ 각 입력을 대응하는 출력으로 매핑한다.
스트림 중간 연산의 실행 특성은? ^ 지연 실행 ^ 선언 즉시 항상 전체 실행 ^ 컴파일 때 결과 고정 ^ 최종 연산이 파이프라인 실행을 이끈다.
소비한 Stream을 다시 최종 연산에 쓰면? ^ 재사용하지 말고 새 Stream이 필요하다 ^ 항상 처음부터 실행된다 ^ 자동으로 캐시한다 ^ 스트림은 한 번 사용하는 처리 흐름이다.
Stream.toList 결과의 변경 가능성은? ^ 변경 불가능 ^ 항상 ArrayList로 변경 가능 ^ 원천만 변경 가능 ^ 결과 목록 변경을 지원하지 않는 계약이다.
병렬 스트림에서 외부 ArrayList에 add하면? ^ 공유 변경으로 안전하지 않을 수 있다 ^ 언제나 안전하다 ^ 순서가 항상 보장된다 ^ 비동기 작업들이 같은 가변 상태를 동시에 변경한다.
빈 정수 스트림을 reduce(0,Integer::sum)하면? ^ 0 ^ null ^ 예외가 필수 ^ 항등값 0이 빈 입력의 결과가 된다.
parallelStream을 선택하기 전 확인할 것은? ^ 분할 비용과 작업 특성 ^ 메서드 이름 길이 ^ 항상 병렬이 빠르다는 가정 ^ 작은 작업이나 I/O 중심 작업에서는 오히려 비용이 커질 수 있다.
peek에서 결제 처리 같은 핵심 동작을 수행하면 안 되는 이유는? ^ 실행을 업무 보장 수단으로 삼기 어렵다 ^ peek가 항상 예외를 던진다 ^ 값 읽기가 금지된다 ^ 단축 평가나 최적화에 따라 호출 범위가 달라질 수 있다.
List.of(1,2,3).stream().filter(n -> n>1).map(n -> n*2).toList()의 결과는? ^ [4, 6] ^ [2, 4, 6] ^ [2, 3] ^ 먼저 2와 3을 남기고 각각 두 배로 변환한다.
원천 목록은 보존하면서 변환 결과를 얻는 방법은? ^ map 후 결과 수집 ^ 원천을 clear한 뒤 순회 ^ forEach에서 원천 삭제 ^ 스트림 변환 결과와 원천 변경을 구분한다.
부수 효과 대신 집계를 표현하는 장점은? ^ 결과와 상태 변경 경계를 추적하기 쉽다 ^ 자동으로 모든 오류가 사라진다 ^ 모든 연산이 병렬화된다 ^ 값으로 결과를 돌려주면 외부 공유 상태 의존을 줄인다.''')
lesson(10,'정렬과 컬렉션 경계','비교자 계약과 복사 전략을 사용해 집계 결과를 정렬한다.', '''정렬은 어떤 순서로 비교할지 명시하는 작업입니다. Comparator.comparing과 thenComparing을 조합하면 1차·2차 기준을 읽기 쉽게 표현할 수 있습니다. 정수를 빼서 비교 결과를 만들면 오버플로가 나서 순서가 뒤집힐 수 있으므로 Integer.compare를 사용합니다. 비교자는 대칭적인 부호와 추이성을 지켜야 합니다.

TreeSet이나 TreeMap은 비교 결과가 0인 값을 같은 정렬 키로 취급합니다. equals와 비교 기준이 다르면 생각과 다른 중복 제거가 생길 수 있습니다. 점수만 비교하는 TreeSet에 같은 점수의 다른 사람을 넣으면 하나가 사라질 수 있으므로 식별 기준을 포함하거나 List를 정렬해야 합니다.

정렬 후 원본 순서를 보존해야 한다면 새 목록을 만들어 정렬하거나 sorted 스트림을 사용합니다. 동점 처리와 null 정책도 테스트 대상입니다. 2주차의 목록·키·제네릭·스트림을 합쳐 빈도 집계 결과를 정렬하고, 입력 컬렉션에 부수 효과가 없는지 확인하세요.''', '''record Score(String name,int value) {}
var source = List.of(new Score("b",90),new Score("a",90),new Score("c",80));
var sorted = source.stream().sorted(Comparator.comparingInt(Score::value).reversed().thenComparing(Score::name)).toList();
if (!sorted.get(0).name().equals("a") || !source.get(0).name().equals("b")) throw new AssertionError();
if (Integer.compare(Integer.MIN_VALUE,Integer.MAX_VALUE)>=0) throw new AssertionError();''','상품별 조회 횟수를 집계하고 횟수 내림차순, 코드 오름차순으로 정렬하세요. 원본 입력은 보존하고 동점 결과가 일관되게 나오게 하세요.','동점 정렬 기준을 명시하고 검증한다.^정수 최솟값과 최댓값 비교에서 오버플로가 없다.^정렬 후에도 원본 순서가 유지된다.','비교는 뺄셈보다 compare를 사용한다.^정렬 집합의 동등성 기준에 주의한다.^동점과 원본 보존을 테스트한다.','java/util/Comparator.html', '''정수 비교를 a-b로 구현할 때 위험은? ^ 오버플로로 순서가 뒤집힌다 ^ 반드시 소수가 된다 ^ 비교가 불가능해진다 ^ 범위를 넘는 뺄셈은 잘못된 부호를 낼 수 있다.
정수의 안전한 비교 메서드는? ^ Integer.compare ^ Integer.sum ^ Math.abs(a-b)만 ^ compare는 뺄셈 오버플로 없이 순서를 반환한다.
2차 정렬 기준을 연결하는 Comparator 메서드는? ^ thenComparing ^ equals만 ^ hashCode ^ 1차 비교가 같은 경우 다음 비교자를 사용한다.
TreeSet이 비교 결과 0인 두 값을 다루는 방식은? ^ 같은 정렬 원소로 취급 ^ 항상 둘 다 보존 ^ 반드시 예외 ^ 정렬 집합은 비교자를 중복 판정에도 사용한다.
점수만 비교하는 TreeSet에 동점 두 사람을 넣을 때 위험은? ^ 한 사람이 중복으로 취급된다 ^ 점수가 자동 합산된다 ^ 이름 순서가 자동 추가된다 ^ 식별 기준이 빠진 비교자는 원치 않는 합침을 만든다.
원본 List를 보존하며 정렬하려면? ^ 복사하거나 sorted 결과를 수집 ^ 원본 sort만 호출 ^ 원본 clear 후 재사용 ^ 정렬 대상과 보존할 입력의 참조를 분리한다.
내림차순 비교자를 얻는 대표 방법은? ^ reversed ^ negateHashCode ^ removeIf ^ reversed는 비교 순서를 뒤집는다.
비교자의 추이성이 깨질 때 생길 수 있는 문제는? ^ 정렬 결과의 일관성이 깨진다 ^ 모든 원소가 자동 복사된다 ^ 메모리 누수가 반드시 사라진다 ^ 정렬 알고리즘은 일관된 순서 관계를 전제로 한다.
동점 결과를 매번 같게 보이려면? ^ 명시적인 2차 기준을 둔다 ^ HashMap의 우연한 순서를 믿는다 ^ 실행 시각으로 무작위 배치 ^ 입력 순서에 의존하지 않는 표시 정책을 만들 수 있다.
Comparator.nullsLast가 표현하는 것은? ^ null을 뒤에 두는 순서 정책 ^ null 제거 ^ null을 빈 문자열로 변경 ^ 필터나 정규화와 달리 비교 순서를 정의한다.
점수 내림차순 후 이름 오름차순에서 (b,90),(a,90)의 첫 항목은? ^ (a,90) ^ (b,90) ^ 항상 무작위 ^ 점수가 같으므로 이름 기준이 적용된다.
중복을 보존하며 정렬해야 할 때 자연스러운 선택은? ^ List 정렬 ^ 점수만 비교하는 TreeSet ^ HashSet 변환만 ^ List는 동점·중복 항목을 지우지 않고 정렬할 수 있다.''')
