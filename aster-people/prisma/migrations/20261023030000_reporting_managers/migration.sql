-- AlterTable
ALTER TABLE "Employee" ADD COLUMN     "reportingManagerId" TEXT;

-- AlterTable
ALTER TABLE "WorkRequest" ADD COLUMN     "managerId" TEXT;

-- CreateIndex
CREATE UNIQUE INDEX "Membership_companyId_id_key" ON "Membership"("companyId", "id");

-- AddForeignKey
ALTER TABLE "Employee" ADD CONSTRAINT "Employee_companyId_reportingManagerId_fkey" FOREIGN KEY ("companyId", "reportingManagerId") REFERENCES "Membership"("companyId", "id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WorkRequest" ADD CONSTRAINT "WorkRequest_companyId_managerId_fkey" FOREIGN KEY ("companyId", "managerId") REFERENCES "Membership"("companyId", "id") ON DELETE RESTRICT ON UPDATE CASCADE;

