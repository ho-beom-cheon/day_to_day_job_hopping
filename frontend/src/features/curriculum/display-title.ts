// The published development fixture is immutable; present its old label without a job title.
export function displayCurriculumTitle(title: string): string {
  return title === "백엔드 개발자 6개월 기초 과정" ? "핵심 역량 학습 과정" : title;
}
