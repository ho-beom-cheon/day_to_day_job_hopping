import { describe, expect, it } from "vitest";
import { toSessionViewModel } from "./model";

describe("session view model", () => {
  it("maps API data to display-only state", () => expect(toSessionViewModel({userId:"1",email:"user@example.com",nickname:" 학습자 ",profileImageUrl:null,role:"USER",curriculumId:null,revision:1,joinedAt:"now",updatedAt:"now"})).toMatchObject({displayName:"학습자",initials:"학",hasCurriculum:false}));
});
