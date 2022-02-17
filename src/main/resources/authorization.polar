actor User{}

resource School{
    permissions = ["read_whole_absence","change_data", "modify_system", "read_all_profiles"];
    roles = ["social", "administration", "system"];

    "modify_system" if "system";
    "read_whole_absence" if "social";
    "change_data" if "administration";
    "read_all_profiles" if "social";
    "administration" if "system";
    "social" if "administration";
}

resource SchoolClass{
    permissions = ["edit_pupils", "edit_info", "read", "read_absence", "post_absence", "read_members"];
    roles = ["class_teacher", "data_delegate", "lesson_teacher", "pupil"];
    relations = {school: School};

    "edit_pupils" if "class_teacher";
    "edit_info" if "class_teacher";
    "post_absence" if "data_delegate";
    "post_absence" if "class_teacher";
    "post_absence" if "social" on "school";
    "read_absence" if "data_delegate";
    "read_absence" if "lesson_teacher";
    "read_members" if "pupil";
    "read_members" if "data_delegate";
    "read_members" if "lesson_teacher";

    "class_teacher" if "administration" on "school";
    "data_delegate" if "social" on "school";
    "data_delegate" if "class_teacher";
    "lesson_teacher" if "class_teacher";
}

has_permission(_:User, "read", _:SchoolClass);

has_relation(Sch: School, "school", _: SchoolClass);

has_role(user: User, role_name: String, _: School) if
    user.type() = role_name;

has_role(user: User, role_name: String, schoolClass: SchoolClass) if
    role in user.classesRoles() and
    role.name() = role_name and
    role.schoolClass().id() = schoolClass.id();

allow(user: User, action: String, schoolClass: SchoolClass) if
    has_permission(user, action, schoolClass);

allow(user: User, action: String, Sch: School) if
    has_permission(user, action, Sch);

allow(user: User, action: String, school : School) if
    has_permission(user, action, school);

allow(user: User, "read", targetUser: User) if
    user.id() = targetUser.id() or
    user.anyMutualClasses(targetUser.classesRoles())  or
    has_permission(user, "read_all_profiles", Sch);

allow(user: User, "invalidate_tokens", targetUser: User) if
    user.id() = targetUser.id() or
    has_permission(user, "modify_system", Sch);

allow(user: User, "read_tokens", targetUser: User) if 
    user.id() = targetUser.id() or
    has_permission(user, "modify_system", Sch);

allow(user: User, "read_integrations", targetUser: User) if
    user.id() = targetUser.id() or
    has_permission(user, "modify_system", Sch);

allow(user: User, "delete_integrations", targetUser: User) if
    user.id() = targetUser.id() or
    has_permission(user, "modify_system", Sch);

allow(user: User, "create_integrations", targetUser: User) if
    user.id() = targetUser.id();
